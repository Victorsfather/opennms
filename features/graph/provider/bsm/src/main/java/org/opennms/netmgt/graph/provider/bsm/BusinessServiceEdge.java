/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2016 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2016 The OpenNMS Group, Inc.
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

package org.opennms.netmgt.graph.provider.bsm;
import org.opennms.netmgt.bsm.service.model.functions.map.MapFunction;
import org.opennms.netmgt.bsm.service.model.graph.GraphEdge;
import org.opennms.netmgt.graph.api.generic.GenericEdge;
import org.opennms.netmgt.graph.simple.AbstractDomainEdge;

public final class BusinessServiceEdge extends AbstractDomainEdge {

    private final static String PROPERTY_MAP_FUNCTION = "mapFunction";
    private final static String PROPERTY_WEIGHT = "weight";
    
    public BusinessServiceEdge(GenericEdge genericEdge) {
        super(genericEdge);
    }

    public MapFunction getMapFunction() {
        return this.delegate.getProperty(PROPERTY_MAP_FUNCTION);
    }

    public float getWeight() {
        return this.delegate.getProperty(PROPERTY_WEIGHT);
    }

    public final static BusinessServiceEdgeBuilder builder() {
        return new BusinessServiceEdgeBuilder();
    }
    
    public static BusinessServiceEdge from(GenericEdge genericEdge) {
        return new BusinessServiceEdge(genericEdge);
    }
    
    public final static class BusinessServiceEdgeBuilder extends AbstractDomainEdgeBuilder<BusinessServiceEdgeBuilder> {
        
        private BusinessServiceEdgeBuilder() {}
        
        BusinessServiceEdgeBuilder graphEdge(GraphEdge graphEdge) {
            mapFunction(graphEdge.getMapFunction());
            weight(graphEdge.getWeight());
            return this;
        }
        
        BusinessServiceEdgeBuilder weight(float weight) {
            this.properties.put(PROPERTY_WEIGHT, weight);
            return this;
        }
       
        BusinessServiceEdgeBuilder mapFunction(MapFunction mapFunction) {
            this.properties.put(PROPERTY_MAP_FUNCTION, mapFunction);
            return this;
        }
        
        public BusinessServiceEdge build() {
            return new BusinessServiceEdge(GenericEdge.builder()
                    .namespace(BusinessServiceGraphProvider.NAMESPACE) // default but can still be changed by properties
                    .properties(properties).source(source).target(target).build());
        }
    }
}
