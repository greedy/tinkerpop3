package com.tinkerpop.gremlin.process.graph.step.branch;

import com.tinkerpop.gremlin.process.Step;
import com.tinkerpop.gremlin.process.Traversal;
import com.tinkerpop.gremlin.process.Traverser;
import com.tinkerpop.gremlin.process.graph.marker.TraversalHolder;
import com.tinkerpop.gremlin.process.graph.step.map.VertexStep;
import com.tinkerpop.gremlin.process.traverser.TraverserRequirement;
import com.tinkerpop.gremlin.process.util.AbstractStep;
import com.tinkerpop.gremlin.process.util.FastNoSuchElementException;
import com.tinkerpop.gremlin.process.util.TraversalHelper;

import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class LocalStep<S, E> extends AbstractStep<S, E> implements TraversalHolder {

    private Traversal.Admin<S, E> localTraversal;
    private boolean first = true;

    public LocalStep(final Traversal traversal, final Traversal<S, E> localTraversal) {
        super(traversal);
        this.localTraversal = localTraversal.asAdmin();
        this.executeTraversalOperations(this.localTraversal, Child.SET_HOLDER, Child.MERGE_IN_SIDE_EFFECTS, Child.SET_SIDE_EFFECTS);
    }

    @Override
    public LocalStep<S, E> clone() throws CloneNotSupportedException {
        final LocalStep<S, E> clone = (LocalStep<S, E>) super.clone();
        clone.localTraversal = this.localTraversal.clone().asAdmin();
        clone.first = true;
        clone.executeTraversalOperations(clone.localTraversal, Child.SET_HOLDER, Child.MERGE_IN_SIDE_EFFECTS, Child.SET_SIDE_EFFECTS);
        return clone;
    }

    @Override
    public String toString() {
        return TraversalHelper.makeStepString(this, this.localTraversal);
    }

    @Override
    public List<Traversal<S, E>> getLocalTraversals() {
        return Collections.singletonList(this.localTraversal);
    }

    @Override
    public Set<TraverserRequirement> getRequirements() {
        return TraversalHelper.getRequirements(this.localTraversal);
    }

    @Override
    public void reset() {
        super.reset();
        this.resetTraversals();
    }

    @Override
    protected Traverser<E> processNextStart() throws NoSuchElementException {
        if (this.first) {
            this.first = false;
            this.localTraversal.addStart(this.starts.next());
        }
        while (true) {
            if (this.localTraversal.hasNext())
                return this.localTraversal.getEndStep().next();
            else if (this.starts.hasNext()) {
                this.localTraversal.reset();
                this.localTraversal.addStart(this.starts.next());
            } else {
                throw FastNoSuchElementException.instance();
            }
        }
    }

    ////////////////

    public boolean isLocalStarGraph() {
        final List<Step> steps = this.localTraversal.getSteps();
        boolean foundOneVertexStep = false;
        for (final Step step : steps) {
            if (step instanceof VertexStep) {
                if (foundOneVertexStep) {
                    return false;
                } else {
                    foundOneVertexStep = true;
                }
            }
        }
        return true;
    }
}
