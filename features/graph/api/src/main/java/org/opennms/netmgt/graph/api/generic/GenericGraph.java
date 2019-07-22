/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2019-2019 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2019 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.graph.api.generic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.opennms.netmgt.graph.api.ImmutableGraph;
import org.opennms.netmgt.graph.api.Vertex;
import org.opennms.netmgt.graph.api.VertexRef;
import org.opennms.netmgt.graph.api.context.DefaultGraphContext;
import org.opennms.netmgt.graph.api.focus.Focus;
import org.opennms.netmgt.graph.api.info.GraphInfo;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;

import edu.uci.ics.jung.graph.DirectedSparseGraph;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * The graph itself is not immutable (Vertices and Edges can be added and removed) but it's properties are. 
 */
public final class GenericGraph extends GenericElement implements ImmutableGraph<GenericVertex, GenericEdge> {

    private final DirectedSparseGraph<VertexRef, GenericEdge> jungGraph;
    private final Map<String, GenericVertex> vertexToIdMap;
    private final Map<String, GenericEdge> edgeToIdMap;
//    private final Map<NodeRef, V> nodeRefToVertexMap = new HashMap<>();

    // A calculation of the focus
    private final Focus focusStrategy;
    private final GraphInfo<GenericVertex> graphInfo;

    private GenericGraph(GenericGraphBuilder builder) {
        super(builder.properties);
        this.jungGraph = builder.jungGraph;
        this.vertexToIdMap = builder.vertexToIdMap;
        this.edgeToIdMap = builder.edgeToIdMap;
        this.focusStrategy = builder.focusStrategy;
        this.graphInfo = new GenericGraphInfo();
    }

    @Override
    public GenericGraph asGenericGraph() {
        return this;
    }

    @Override
    public List<GenericVertex> getVertices() {
        // TODO MVR use junggraph.getVetices instead. However addEdge is adding the edges if not in same namespace
        // We have to figure out a workaround for that somehow
        return new ArrayList<>(vertexToIdMap.values());
    }

    @Override
    public List<GenericEdge> getEdges() {
        // TODO MVR use junggraph.getEdges instead. However addEdge is adding the edges if not in same namespace
        // We have to figure out a workaround for that somehow
        return new ArrayList<>(edgeToIdMap.values());
    }

    public GraphInfo getGraphInfo(){
        return graphInfo;
    }

    @Override
    public String getDescription() {
        return graphInfo.getDescription();
    }

    @Override
    public String getLabel() {
        return graphInfo.getLabel();
    }

    @Override
    public Class<GenericVertex> getVertexType() {
        return GenericVertex.class;
    }

    @Override
    public List<Vertex> getDefaultFocus() {
        if (focusStrategy != null) {
            return focusStrategy.getFocus(new DefaultGraphContext(this)).stream().map(vr -> vertexToIdMap.get(vr.getId())).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

//    @Override
//    public Vertex getVertex(NodeRef nodeRef) {
//        return nodeRefToVertexMap.get(nodeRef);
//    }



    @Override
    public GenericVertex getVertex(String id) {
        return vertexToIdMap.get(id);
    }

    @Override
    public GenericEdge getEdge(String id) {
        return edgeToIdMap.get(id);
    }

    @Override
    public List<String> getVertexIds() {
        return vertexToIdMap.keySet().stream().sorted().collect(Collectors.toList());
    }

    @Override
    public List<String> getEdgeIds() {
        return edgeToIdMap.keySet().stream().sorted().collect(Collectors.toList());
    }

    @Override
    public List<GenericVertex> resolveVertices(Collection<String> vertexIds) {
        final List<GenericVertex> collect = vertexIds.stream().map(vid -> vertexToIdMap.get(vid)).filter(v -> v != null).collect(Collectors.toList());
        return collect;
    }

    @Override
    public GenericVertex resolveVertex(VertexRef vertexRef) {
        Objects.requireNonNull(vertexRef);
        if (getNamespace().equalsIgnoreCase(vertexRef.getNamespace())) {
            final GenericVertex resolvedVertex = resolveVertices(Lists.newArrayList(vertexRef.getId())).stream().findAny().orElse(null);
            return resolvedVertex;
        }
        return null;
    }

    public List<GenericVertex> resolveVertexRefs(Collection<VertexRef> vertexRefs) {
        // Determine all vertexId for all vertices with the same namespace as the current graph
        List<String> vertexIds = vertexRefs.stream()
                .filter(ref -> getNamespace().equalsIgnoreCase(ref.getNamespace()))
                .map(ref -> ref.getId())
                .collect(Collectors.toList());
        return resolveVertices(vertexIds);
    }

    @Override
    public List<GenericEdge> resolveEdges(Collection<String> vertexIds) {
        final List<GenericEdge> collect = vertexIds.stream().map(eid -> edgeToIdMap.get(eid)).collect(Collectors.toList());
        return collect;
    }

    @Override
    public Collection<GenericVertex> getNeighbors(GenericVertex eachVertex) {
        return resolveVertexRefs(jungGraph.getNeighbors(eachVertex.getVertexRef()));
    }

    @Override
    public Collection<GenericEdge> getConnectingEdges(GenericVertex eachVertex) {
        final Set<GenericEdge> edges = new HashSet<>();
        if (eachVertex != null) {
            final VertexRef genericVertexRef = eachVertex.getVertexRef();
            edges.addAll(jungGraph.getInEdges(genericVertexRef));
            edges.addAll(jungGraph.getOutEdges(genericVertexRef));
        }
        return edges;
    }

    @Override
    public ImmutableGraph<GenericVertex, GenericEdge> getSnapshot(Collection<GenericVertex> verticesInFocus, int szl) {
        // TODO MVR implement me
//        return new SemanticZoomLevelTransformer(verticesInFocus, szl).transform(this, () -> {
//            final SimpleGraph<SimpleVertex, SimpleEdge<SimpleVertex>> snapshotGraph = new SimpleGraph<>(getNamespace());
//            applyInfo(SimpleGraph.this);
//            return snapshotGraph;
//        });
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        GenericGraph that = (GenericGraph) o;
        return Objects.equals(vertexToIdMap, that.vertexToIdMap)
                && Objects.equals(edgeToIdMap, that.edgeToIdMap)
                && Objects.equals(focusStrategy, that.focusStrategy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(),
                vertexToIdMap, edgeToIdMap, focusStrategy);
    }
    
    public static GenericGraphBuilder builder() {
        return new GenericGraphBuilder();
    }
    
    public final static class GenericGraphBuilder extends GenericElementBuilder<GenericGraphBuilder> {
         
        private final DirectedSparseGraph<VertexRef, GenericEdge> jungGraph = new DirectedSparseGraph<>();
        private final Map<String, GenericVertex> vertexToIdMap = new HashMap<>();
        private final Map<String, GenericEdge> edgeToIdMap = new HashMap<>();
//        private final Map<NodeRef, V> nodeRefToVertexMap = new HashMap<>();

        // A calculation of the focus
        private Focus focusStrategy;
        
        // TODO: Patrick make sure we don't change the namespace after adding edges -> they have been validated against the namespace
        private GenericGraphBuilder() {}
     
        public GenericGraphBuilder graph(GenericGraph graph) {
            this.properties.putAll(graph.getProperties());
            this.addVertices(graph.getVertices());
            this.addEdges(graph.getEdges());
            this.focusStrategy = graph.focusStrategy; // assuming focus strategy is immutable => not so relevant since the implementation will change anyway with next pull request
            return this;
        }
        
        public GenericGraphBuilder description(String description) {
            property(GenericProperties.DESCRIPTION, description);
            return this;
        }
        
        public GenericGraphBuilder graphInfo(GraphInfo graphInfo) {
            namespace(graphInfo.getNamespace());
            description(graphInfo.getDescription());
            label(graphInfo.getLabel());
            return this;
        }
        
        public GenericGraphBuilder setFocusStrategy(Focus focusStrategy) {
            this.focusStrategy = focusStrategy; // TODO MVR verify persistence of this
            return this;
        }

        public GenericGraphBuilder addEdges(Collection<GenericEdge> edges) {
            for (GenericEdge eachEdge : edges) {
                addEdge(eachEdge);
            }
            return this;
        }

        public GenericGraphBuilder addVertices(Collection<GenericVertex> vertices) {
            for (GenericVertex eachVertex : vertices) {
                addVertex(eachVertex);
            }
            return this;
        }

        public GenericGraphBuilder addVertex(GenericVertex vertex) {
            Objects.requireNonNull(vertex, "Vertex can not be null");
            checkArgument(!Strings.isNullOrEmpty(vertex.getId()) , "GenericVertex.getId() can not be empty or null. Vertex= %s", vertex);
            Objects.requireNonNull(vertex.getId(), "Vertex id can not be null");
            if (jungGraph.containsVertex(vertex.getVertexRef())) return this; // already added
            jungGraph.addVertex(vertex.getVertexRef());
            vertexToIdMap.put(vertex.getId(), vertex);

//            if (vertex.getNodeRef() != null) {
//                // TODO MVR implement me
////                nodeRefToVertexMap.put(vertex.getNodeRef(), vertex);
//            }
            return this; 
        }

        public GenericGraphBuilder addEdge(GenericEdge edge) {
            Objects.requireNonNull(getNamespace(), "Please set a namespace before adding elements to this graph.");
            Objects.requireNonNull(edge, "GenericEdge cannot be null");
            checkArgument(!Strings.isNullOrEmpty(edge.getId()) , "GenericEdge.getId() can not be empty or null. Vertex= %s", edge);
            Objects.requireNonNull(edge.getId());
            if (jungGraph.containsEdge(edge)) return this; // already added
            if(!this.getNamespace().equals(edge.getNamespace())){
                throw new IllegalArgumentException(
                        String.format("The namespace of the edge (%s) doesn't match the namespace of this graph (%s). Edge: %s ",
                        edge.getNamespace(), this.getNamespace(), edge.toString()));
            }
            assertVertexFromSameNamespaceIsKnown(edge.getSource());
            assertVertexFromSameNamespaceIsKnown(edge.getTarget());
            jungGraph.addEdge(edge, edge.getSource(), edge.getTarget());
            edgeToIdMap.put(edge.getId(), edge);
            return this;
        }

        private void assertVertexFromSameNamespaceIsKnown(VertexRef vertex){
            if (vertex.getNamespace().equalsIgnoreCase(getNamespace()) && getVertex(vertex.getId()) == null) {
                throw new IllegalArgumentException(
                        String.format("Adding a VertexRef to an unknown Vertex with id=%s in our namespace (%s). Please add the Vertex first to the graph",
                                vertex.getId(), this.getNamespace()));
            }
        }
        
        public void removeEdge(GenericEdge edge) {
            Objects.requireNonNull(edge);
            jungGraph.removeEdge(edge);
            edgeToIdMap.remove(edge.getId());
        }
        
        public void removeVertex(GenericVertex vertex) {
            Objects.requireNonNull(vertex);
            jungGraph.removeVertex(vertex.getVertexRef());
            vertexToIdMap.remove(vertex.getId());
        }
        
        public String getNamespace() {
            return Objects.requireNonNull((String)this.properties.get(GenericProperties.NAMESPACE), "Namespace is not set yet. Please call namespace(...) first.");
        }
        
        public GenericVertex getVertex(String id) {
            return vertexToIdMap.get(id);
        }
        
        public GenericGraphBuilder namespace(String namespace) {
            checkIfNamespaceChangeIsAllowed(namespace);
            return super.namespace(namespace);
        }
    
        public GenericGraphBuilder property(String name, Object value) {
            if(GenericProperties.NAMESPACE.equals(name)) {
                checkIfNamespaceChangeIsAllowed((String)value);
            }
            return super.property(name, value);
        }
        
        public GenericGraphBuilder properties(Map<String, Object> properties) {
            if(properties != null && properties.containsKey(GenericProperties.NAMESPACE)) {
                checkIfNamespaceChangeIsAllowed((String)properties.get(GenericProperties.NAMESPACE));
            }
            return super.properties(properties);
        }
        
        private void checkIfNamespaceChangeIsAllowed(String newNamespace) {
            if(!this.edgeToIdMap.isEmpty() && !Objects.equals(getNamespace(), newNamespace)) {
                throw new IllegalStateException("Cannot change namespace after adding Elements to Graph.");
            }
        }
        
        public GenericGraph build() {
            return new GenericGraph(this);
        }
    }
    
    private class GenericGraphInfo implements GraphInfo<GenericVertex> {

        @Override
        public String getNamespace() {
            return (String) properties.get(GenericProperties.NAMESPACE);
        }

        @Override
        public String getDescription() {
            return (String) properties.get(GenericProperties.DESCRIPTION);
        }

        @Override
        public String getLabel() {
            return (String) properties.get(GenericProperties.LABEL);
        }

        @Override
        public Class<GenericVertex> getVertexType() {
            return GenericVertex.class;
        }

    }
}
