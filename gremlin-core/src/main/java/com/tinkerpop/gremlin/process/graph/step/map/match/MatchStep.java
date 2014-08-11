package com.tinkerpop.gremlin.process.graph.step.map.match;

import com.tinkerpop.gremlin.process.SimpleTraverser;
import com.tinkerpop.gremlin.process.Traversal;
import com.tinkerpop.gremlin.process.Traverser;
import com.tinkerpop.gremlin.process.util.AbstractStep;
import com.tinkerpop.gremlin.process.util.FastNoSuchElementException;
import com.tinkerpop.gremlin.process.util.SingleIterator;
import com.tinkerpop.gremlin.process.util.TraversalHelper;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Stack;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public class MatchStep<S, E> extends AbstractStep<S, Map<String, E>> {

    static final BiConsumer<String, Object> TRIVIAL_CONSUMER = (s, t) -> {
    };

    private static final String ANON_LABEL_PREFIX = "_";

    // optimize before processing each start object, by default
    private static final int DEFAULT_STARTS_PER_OPTIMIZE = 1;

    private final String startAs;
    private final Map<String, List<TraversalWrapper<S, S>>> traversalsByStartAs;

    private int startsPerOptimize = DEFAULT_STARTS_PER_OPTIMIZE;
    private int optimizeCounter = -1;
    private int anonLabelCounter = 0;

    private Enumerator<S> currentSolution;
    private int currentIndex;

    // initial value allows MatchStep to be used as a stand-alone query engine
    private Traverser<S> currentStart = new SimpleTraverser<>(null);

    public MatchStep(final Traversal traversal, final String startAs, final Traversal... traversals) {
        super(traversal);
        this.startAs = startAs;
        this.traversalsByStartAs = new HashMap<>();
        for (final Traversal tl : traversals) {
            addTraversal(tl);
        }
        // given all the wrapped traversals, determine bad patterns in the set and throw exceptions if not solvable
        checkSolvability();
    }

    public void setStartsPerOptimize(final int startsPerOptimize) {
        if (startsPerOptimize < 1) {
            throw new IllegalArgumentException();
        }

        this.startsPerOptimize = startsPerOptimize;
    }

    @Override
    protected Traverser<Map<String, E>> processNextStart() throws NoSuchElementException {
        final Map<String, E> map = new HashMap<>();
        final Traverser<Map<String, E>> result = this.currentStart.makeChild(this.getAs(), map);
        final BiConsumer<String, S> resultSetter = (name, value) -> map.put(name, (E) value);

        while (true) { // break out when the current solution is exhausted and there are no more starts
            if (null == this.currentSolution || (this.currentIndex >= this.currentSolution.size() && this.currentSolution.isComplete())) {
                if (this.starts.hasNext()) {
                    this.optimizeCounter = (this.optimizeCounter + 1) % this.startsPerOptimize;
                    if (0 == this.optimizeCounter) {
                        optimize();
                    }

                    this.currentStart = this.starts.next();
                    this.currentSolution = solveFor(new SingleIterator<>(this.currentStart.get()));
                    this.currentIndex = 0;
                } else {
                    throw FastNoSuchElementException.instance();
                }
            }

            map.clear();
            if (this.currentSolution.visitSolution(this.currentIndex++, resultSetter)) {
                return result;
            }
        }
    }

    /**
     * @return a description of the current state of this step, including the query plan and gathered statistics
     */
    public String summarize() {
        final StringBuilder sb = new StringBuilder("match \"")
                .append(this.startAs)
                .append("\":\t")
                .append(findCost(this.startAs))
                .append("\n");
        summarize(this.startAs, sb, new HashSet<>(), 1);
        return sb.toString();
    }

    private void summarize(final String outLabel,
                           final StringBuilder sb,
                           final Set<String> visited,
                           final int indent) {
        if (!visited.contains(outLabel)) {
            visited.add(outLabel);
            final List<TraversalWrapper<S, S>> outs = traversalsByStartAs.get(outLabel);
            if (null != outs) {
                for (final TraversalWrapper<S, S> w : outs) {
                    for (int i = 0; i < indent; i++) sb.append("\t");
                    sb.append(outLabel).append("->").append(w.endAs).append(":\t");
                    sb.append(findCost(w));
                    sb.append("\t").append(w);
                    sb.append("\n");
                    summarize(w.endAs, sb, visited, indent + 1);
                }
            }
        }
    }

    private void addTraversal(final Traversal<S, S> traversal) {
        String startAs = TraversalHelper.getStart(traversal).getAs();
        String endAs = TraversalHelper.getEnd(traversal).getAs();
        if (!TraversalHelper.isLabeled(startAs)) {
            throw new IllegalArgumentException("All match traversals must have their start step labeled with as()");
        }
        endAs = TraversalHelper.isLabeled(endAs) ? endAs : null;
        checkAs(startAs);
        if (null == endAs) {
            endAs = createAnonymousAs();
        } else {
            checkAs(endAs);
        }

        final TraversalWrapper<S, S> wrapper = new TraversalWrapper<>(traversal, startAs, endAs);
        // index all wrapped traversals by their startAs
        List<TraversalWrapper<S, S>> l2 = this.traversalsByStartAs.get(startAs);
        if (null == l2) {
            l2 = new LinkedList<>();
            this.traversalsByStartAs.put(startAs, l2);
        }
        l2.add(wrapper);
    }

    private void checkSolvability() {
        final Set<String> pathSet = new HashSet<>();
        final Stack<String> stack = new Stack<>();
        stack.push(this.startAs);
        int countTraversals = 0;
        while (!stack.isEmpty()) {
            final String outAs = stack.peek();
            if (pathSet.contains(outAs)) {
                stack.pop();
                pathSet.remove(outAs);
            } else {
                pathSet.add(outAs);
                final List<TraversalWrapper<S, S>> l = traversalsByStartAs.get(outAs);
                if (null != l) {
                    for (final TraversalWrapper<S, S> tw : l) {
                        countTraversals++;
                        if (pathSet.contains(tw.endAs)) {
                            throw new IllegalArgumentException("The provided traversal set contains a cycle due to '" + tw.endAs + "'");
                        }
                        stack.push(tw.endAs);
                    }
                }
            }
        }

        int totalTraversals = 0;
        for (List<TraversalWrapper<S, S>> l : this.traversalsByStartAs.values()) {
            totalTraversals += l.size();
        }

        if (countTraversals < totalTraversals) {
            throw new IllegalArgumentException("The provided traversal set contains unreachable as-label(s)");
        }
    }

    private void checkAs(final String as) {
        // note: this won't happen so long as the anon prefix is the same as Traversal.UNDERSCORE
        if (isAnonymousAs(as)) {
            throw new IllegalArgumentException("The step named '" + as + "' uses reserved prefix '" + ANON_LABEL_PREFIX + "'");
        }
    }

    private static boolean isAnonymousAs(final String as) {
        return as.startsWith(ANON_LABEL_PREFIX);
    }

    private String createAnonymousAs() {
        return ANON_LABEL_PREFIX + ++this.anonLabelCounter;
    }

    /**
     * Directly applies this match query to a sequence of inputs
     *
     * @param inputs a sequence of inputs
     * @return an enumerator over all solutions
     */
    public Enumerator<S> solveFor(final Iterator<S> inputs) {
        return solveFor(startAs, inputs);
    }

    private Enumerator<S> solveFor(final String localStartAs,
                                   final Iterator<S> inputs) {
        List<TraversalWrapper<S, S>> outs = traversalsByStartAs.get(localStartAs);
        if (null == outs) {
            // no out-traversals from here; just enumerate the values bound to localStartAs
            return isAnonymousAs(localStartAs)
                ? new SimpleEnumerator<>(localStartAs, inputs)
                : new IteratorEnumerator<>(localStartAs, inputs);
        } else {
            // for each value bound to localStartAs, feed it into all out-traversals in parallel and join the results
            return new SerialEnumerator<>(localStartAs, inputs, o -> {
                Enumerator<S> result = null;
                Set<String> leftLabels = new HashSet<>();

                for (TraversalWrapper<S, S> w : outs) {
                    TraversalUpdater<S, S> updater
                            = new TraversalUpdater<>(w, new SingleIterator<>(o), currentStart, this.getAs());

                    Set<String> rightLabels = new HashSet<>();
                    addVariables(w.endAs, rightLabels);
                    Enumerator<S> ie = solveFor(w.endAs, updater);
                    result = null == result ? ie : crossJoin(result, ie, leftLabels, rightLabels);
                    leftLabels.addAll(rightLabels);
                }

                return result;
            });
        }
    }

    private <T> Enumerator<T> crossJoin(final Enumerator<T> left,
                                        final Enumerator<T> right,
                                        final Set<String> leftLabels,
                                        final Set<String> rightLabels) {
        Set<String> shared = new HashSet<>();
        for (String s : rightLabels) {
            if (leftLabels.contains(s)) {
                shared.add(s);
            }
        }

        Enumerator<T> cj = new CrossJoinEnumerator<>(left, right);
        return shared.size() > 0 ? new InnerJoinEnumerator<>(cj, shared) : cj;
    }

    // recursively add all non-anonymous variables from a starting point in the query
    private void addVariables(final String localStartAs,
                              final Set<String> variables) {
        if (!isAnonymousAs(localStartAs)) {
            variables.add(localStartAs);
        }

        List<TraversalWrapper<S, S>> outs = traversalsByStartAs.get(localStartAs);
        if (null != outs) {
            for (TraversalWrapper<S, S> w : outs) {
                String endAs = w.endAs;
                if (!variables.contains(endAs)) {
                    addVariables(endAs, variables);
                }
            }
        }
    }

    // applies a visitor, skipping anonymous variables
    static <T> void visit(final String name,
                          final T value,
                          final BiConsumer<String, T> visitor) {
        if (!isAnonymousAs(name)) {
            visitor.accept(name, value);
        }
    }

    /**
     * Computes and applies a new query plan based on gathered statistics about traversal inputs and outputs.
     */
    // note: optimize() is never called from within a solution iterator, as it changes the query plan
    public void optimize() {
        optimizeAt(startAs);
    }

    private void optimizeAt(final String outAs) {
        List<TraversalWrapper<S, S>> outs = traversalsByStartAs.get(outAs);
        if (null != outs) {
            for (TraversalWrapper<S, S> t : outs) {
                optimizeAt(t.endAs);
                updateOrderingFactor(t);
            }
            Collections.sort(outs);
        }
    }

    private double findCost(final TraversalWrapper<S, S> root) {
        double bf = root.findBranchFactor();
        return bf + findCost(root.endAs, root.findBranchFactor());
    }

    private double findCost(final String outAs,
                            final double branchFactor) {
        double bf = branchFactor;

        double cost = 0;

        List<TraversalWrapper<S, S>> outs = traversalsByStartAs.get(outAs);
        if (null != outs) {
            for (TraversalWrapper<S, S> child : outs) {
                cost += bf * findCost(child);
                bf *= child.findBranchFactor();
            }
        }

        return cost;
    }

    /**
     * @param outLabel the out-label of one or more traversals in the query
     * @return the expected cost, in the current query plan, of applying the branch of the query plan at
     * the given out-label to one start value
     */
    public double findCost(final String outLabel) {
        return findCost(outLabel, 1.0);
    }

    private void updateOrderingFactor(final TraversalWrapper<S, S> w) {
        w.orderingFactor = ((w.findBranchFactor() - 1) / findCost(w));
    }

    /**
     * A wrapper for a traversal in a query which maintains statistics about the traversal as
     * it consumes inputs and produces outputs.
     * The "branch factor" of the traversal is an important factor in determining its place in the query plan.
     */
    // note: input and output counts are never "refreshed".
    // The position of a traversal in a query never changes, although its priority / likelihood of being executed does.
    // Priority in turn affects branch factor.
    // However, with sufficient inputs and optimizations,the branch factor is expected to converge on a stable value.
    public static class TraversalWrapper<A, B> implements Comparable<TraversalWrapper<A, B>>, Serializable {
        private final Traversal<A, B> traversal;
        private final String startAs, endAs;
        private int totalInputs = 0;
        private int totalOutputs = 0;
        private double orderingFactor;

        public TraversalWrapper(final Traversal<A, B> traversal,
                                final String startAs,
                                final String endAs) {
            this.traversal = traversal;
            this.startAs = startAs;
            this.endAs = endAs;
        }

        public void incrementInputs() {
            this.totalInputs++;
        }

        public void incrementOutputs(int outputs) {
            this.totalOutputs += outputs;
        }

        // TODO: take variance into account, to avoid penalizing traversals for early encounters with super-inputs, or simply for never having been tried
        public double findBranchFactor() {
            return 0 == this.totalInputs ? 1 : this.totalOutputs / ((double) this.totalInputs);
        }

        public int compareTo(final TraversalWrapper<A, B> other) {
            return ((Double) this.orderingFactor).compareTo(other.orderingFactor);
        }

        public Traversal<A, B> getTraversal() {
            return this.traversal;
        }

        public void exhaust() {
            // TODO: we need a Traversal.reset() to make exhausting the traversal unnecessary;
            // the latter defeats the purpose of joins which consume only as many iterator elements as necessary
            while (this.traversal.hasNext()) this.traversal.next();
        }

        @Override
        public String toString() {
            return "[" + this.startAs + "->" + this.endAs + "," + findBranchFactor() + "," + this.totalInputs + "," + this.totalOutputs + "," + this.traversal + "]";
        }
    }

    /**
     * A helper object which wraps a traversal, submitting starts and counting results per start
     */
    public static class TraversalUpdater<A, B> implements Iterator<B> {
        private final TraversalWrapper<A, B> w;
        private int outputs = -1;

        public TraversalUpdater(final TraversalWrapper<A, B> w,
                                final Iterator<A> inputs,
                                final Traverser<A> start,
                                final String as) {
            this.w = w;

            Iterator<A> seIter = new SideEffectIterator<>(inputs, ignored -> {
                // only increment traversal input and output counts once an input has been completely processed by the traversal
                if (-1 != outputs) {
                    w.incrementInputs();
                    w.incrementOutputs(outputs);
                }
                outputs = 0;
            });
            Iterator<Traverser<A>> starts = new MapIterator<>(seIter,
                    o -> start.makeChild(as, o));

            w.exhaust();

            // with the traversal "empty" and ready for re-use, add new starts
            w.traversal.addStarts(starts);
        }

        // note: may return true after first returning false (inheriting this behavior from e.g. DefaultTraversal)
        public boolean hasNext() {
            return w.traversal.hasNext();
        }

        public B next() {
            outputs++;
            B b = w.traversal.next();

            // immediately check hasNext(), possibly updating the traverser's statistics even if we otherwise abandon the iterator
            w.traversal.hasNext();

            return b;
        }
    }

    // an iterator which executes a side-effect the first time hasNext() is called before a next()
    private static class SideEffectIterator<T> implements Iterator<T> {
        private final Consumer onHasNext;
        private final Iterator<T> baseIterator;
        private boolean ready = true;

        private SideEffectIterator(final Iterator<T> baseIterator,
                                   final Consumer onHasNext) {
            this.onHasNext = onHasNext;
            this.baseIterator = baseIterator;
        }

        public boolean hasNext() {
            if (this.ready) {
                this.onHasNext.accept(null);
                this.ready = false;
            }
            return this.baseIterator.hasNext();
        }

        public T next() {
            T value = this.baseIterator.next();
            this.ready = true;
            return value;
        }
    }

    private static class MapIterator<A, B> implements Iterator<B> {
        private final Function<A, B> map;
        private final Iterator<A> baseIterator;

        public MapIterator(final Iterator<A> baseIterator, final Function<A, B> map) {
            this.map = map;
            this.baseIterator = baseIterator;
        }

        public boolean hasNext() {
            return this.baseIterator.hasNext();
        }

        public B next() {
            return this.map.apply(this.baseIterator.next());
        }
    }
}