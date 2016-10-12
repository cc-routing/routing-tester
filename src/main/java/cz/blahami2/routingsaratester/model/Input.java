/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cz.blahami2.routingsaratester.model;

import cz.certicon.routing.model.values.LengthUnits;
import cz.certicon.routing.model.values.TimeUnits;
import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 *
 * @author Michael Blaha {@literal <michael.blaha@gmail.com>}
 */
public class Input implements Iterable<InputElement> {

    private final TLongObjectMap<InputElement> map;

    private Input( TLongObjectMap<InputElement> map ) {
        this.map = map;
    }

    public InputElement getInputElement( long inputId ) {
        return map.get( inputId );
    }

    @Override
    public Iterator<InputElement> iterator() {
        return map.valueCollection().iterator();
    }

    @Override
    public String toString() {
        return "{"
                + StreamSupport.stream( spliterator(), false )
                .sorted( Comparator.comparing( InputElement::getId ) )
                .map( e -> "{" + e.getId() + " "
                        + e.getSourceNodeId() + " "
                        + e.getTargetNodeId() + " "
                        + e.getLength().getValue( LengthUnits.METERS ) + " "
                        + e.getTime().getValue( TimeUnits.SECONDS ) + " "
                        + e.getEdgeIds().stream().map( x -> x.toString() ).collect( Collectors.joining( " " ) ) + "}" )
                .collect( Collectors.joining( "," ) )
                + "}";
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 89 * hash + Objects.hashCode( this.map );
        return hash;
    }

    @Override
    public boolean equals( Object obj ) {
        if ( this == obj ) {
            return true;
        }
        if ( obj == null ) {
            return false;
        }
        if ( getClass() != obj.getClass() ) {
            return false;
        }
        final Input other = (Input) obj;
        for ( long key : map.keys() ) {
            if ( !other.map.containsKey( key ) || !map.get( key ).equals( other.map.get( key ) ) ) {
                return false;
            }
        }
        return true;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final TLongObjectMap<InputElement> m;

        public Builder() {
            this.m = new TLongObjectHashMap<>();
        }

        public Builder add( InputElement inputElement ) {
            m.put( inputElement.getId(), inputElement );
            return this;
        }

        public Builder add( Collection<? extends InputElement> inputElements ) {
            inputElements.stream().forEach( ( inputElement ) -> m.put( inputElement.getId(), inputElement ) );
            return this;
        }

        public Builder add( InputElement... inputElements ) {
            Arrays.stream( inputElements ).forEach( ( inputElement ) -> m.put( inputElement.getId(), inputElement ) );
            return this;
        }

        public Input build() {
            return new Input( m );
        }
    }
}
