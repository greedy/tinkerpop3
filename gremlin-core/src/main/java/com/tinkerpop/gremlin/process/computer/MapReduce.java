package com.tinkerpop.gremlin.process.computer;

import com.tinkerpop.gremlin.structure.Vertex;
import org.apache.commons.configuration.Configuration;
import org.javatuples.Pair;

import java.io.Serializable;
import java.util.Iterator;

/**
 * A MapReduce is composed of map(), combine(), and reduce() stages.
 * The map() stage processes the vertices of the {@link com.tinkerpop.gremlin.structure.Graph} in a logically parallel manner.
 * The combine() stage aggregates the values of a particular map emitted key prior to sending across the cluster.
 * The reduce() stage aggregates the values of the combine/map emitted keys for the keys that hash to the current machine in the cluster.
 * The interface presented here is nearly identical to the interface popularized by Hadoop save the the map() is over the vertices of the graph.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface MapReduce<MK, MV, RK, RV, R> {

    /**
     * MapReduce is composed of three stages: map, combine, and reduce.
     */
    public static enum Stage {
        MAP, COMBINE, REDUCE
    }

    /**
     * When it is necessary to store the state of a MapReduce job, this method is called.
     * This is typically required when the MapReduce job needs to be serialized to another machine.
     * Note that what is stored is simply the instance state, not any processed data.
     *
     * @param configuration the configuration to store the state of the MapReduce job in.
     */
    public default void storeState(final Configuration configuration) {

    }

    /**
     * When it is necessary to load the state of a MapReduce job, this method is called.
     * This is typically required when the MapReduce job needs to be serialized to another machine.
     * Note that what is loaded is simply the instance state, not any processed data.
     *
     * @param configuration the configuration to load the state of the MapReduce job from.
     */
    public default void loadState(final Configuration configuration) {

    }

    /**
     * A MapReduce job can be map-only, map-reduce-only, or map-combine-reduce.
     * Before executing the particular stage, this method is called to determine if the respective stage is defined.
     * This method should return true if the respective stage as a non-default method implementation.
     *
     * @param stage the stage to check for definition.
     * @return whether that stage should be executed.
     */
    public boolean doStage(final Stage stage);

    /**
     * The map() method is logically executed at all vertices in the graph in parallel.
     * The map() method emits key/value pairs given some analysis of the data in the vertices (and/or its incident edges).
     *
     * @param vertex  the current vertex being map() processed.
     * @param emitter the component that allows for key/value pairs to be emitted to the next stage.
     */
    public default void map(final Vertex vertex, final MapEmitter<MK, MV> emitter) {
    }

    /**
     * The combine() method is logically executed at all "machines" in parallel.
     * The combine() method pre-combines the values for a key prior to propagation over the wire.
     * The combine() method must emit the same key/value pairs as the reduce() method.
     * If there is a combine() implementation, there must be a reduce() implementation.
     * If the MapReduce implementation is single machine, it can skip executing this method as reduce() is sufficient.
     *
     * @param key     the key that has aggregated values
     * @param values  the aggregated values associated with the key
     * @param emitter the component that allows for key/value pairs to be emitted to the reduce stage.
     */
    public default void combine(final MK key, final Iterator<MV> values, final ReduceEmitter<RK, RV> emitter) {
    }

    /**
     * The reduce() method is logically on the "machine" the respective key hashes to.
     * The reduce() method combines all the values associated with the key and emits key/value pairs.
     *
     * @param key     the key that has aggregated values
     * @param values  the aggregated values associated with the key
     * @param emitter the component that allows for key/value pairs to be emitted as the final result.
     */
    public default void reduce(final MK key, final Iterator<MV> values, final ReduceEmitter<RK, RV> emitter) {
    }

    /**
     * The key/value pairs emitted by reduce() (or map() in a map-only job) can be iterated to generated a local JVM Java object.
     *
     * @param keyValues the key/value pairs that were emitted from reduce() (or map() in a map-only job)
     * @return the sideEffect object formed from the emitted key/values.
     */
    public R generateSideEffect(final Iterator<Pair<RK, RV>> keyValues);

    /**
     * The results of the MapReduce job are associated with a sideEffect-key to ultimately be stored in {@link Memory}.
     *
     * @return the sideEffect key of the generated sideEffect object.
     */
    public String getSideEffectKey();

    /**
     * The sideEffect can be generated and added to {@link Memory} and accessible via {@link ComputerResult}.
     * The default simply takes the object from generateSideEffect() and adds it to the Memory given getSideEffectKey().
     *
     * @param memory    the memory of the {@link GraphComputer}
     * @param keyValues the key/value pairs emitted from reduce() (or map() in a map only job).
     */
    public default void addSideEffectToMemory(final Memory memory, final Iterator<Pair<RK, RV>> keyValues) {
        memory.set(this.getSideEffectKey(), this.generateSideEffect(keyValues));
    }

    //////////////////

    /**
     * The MapEmitter is used to emit key/value pairs from the map() stage of the MapReduce job.
     * The implementation of MapEmitter is up to the vendor, not the developer.
     *
     * @param <K> the key type
     * @param <V> the value type
     */
    public interface MapEmitter<K, V> {
        public void emit(final K key, final V value);
    }

    /**
     * The ReduceEmitter is used to emit key/value pairs from the combine() and reduce() stages of the MapReduce job.
     * The implementation of ReduceEmitter is up to the vendor, not the developer.
     *
     * @param <OK> the key type
     * @param <OV> the value type
     */
    public interface ReduceEmitter<OK, OV> {
        public void emit(final OK key, OV value);
    }

    //////////////////

    /**
     * A convenience singleton when a single key is needed so that all emitted values converge to the same combiner/reducer.
     */
    public static class NullObject implements Comparable<NullObject>, Serializable {
        private static final NullObject INSTANCE = new NullObject();
        private static final String NULL_OBJECT = "MapReduce$NullObject";

        public static NullObject instance() {
            return INSTANCE;
        }

        @Override
        public int hashCode() {
            return 666666666;
        }

        @Override
        public boolean equals(final Object object) {
            return object instanceof NullObject;
        }

        @Override
        public int compareTo(final NullObject nullObject) {
            return 0;
        }

        @Override
        public String toString() {
            return NULL_OBJECT;
        }
    }
}
