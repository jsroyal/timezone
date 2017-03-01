/*
 * Copyright 2014-2016 GraphHopper GmbH
 *
 * NOTICE:  All information contained herein is, and remains the property
 * of GraphHopper GmbH. The intellectual and technical concepts contained
 * herein are proprietary to GraphHopper GmbH, and are protected by trade secret
 * or copyright law. Dissemination of this information or reproduction of
 * this material is strictly forbidden unless prior written permission
 * is obtained from GraphHopper GmbH.
 */
package com.graphhopper.timezone.resources;

import com.codahale.metrics.annotation.Timed;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.index.quadtree.Quadtree;
import io.dropwizard.jersey.errors.ErrorMessage;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.opengis.feature.simple.SimpleFeature;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.time.LocalTime;
import java.util.List;
import java.util.TimeZone;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

@Path("/timezone")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TimeZoneService {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(TimeZoneService.class);

    private Quadtree quadtree;

    private GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory(null);

    public TimeZoneService(Quadtree quadtree) {
        this.quadtree = quadtree;
    }

    @GET
    @Timed
    public Response handle(@Context UriInfo uriInfo){
        List<String> location = uriInfo.getQueryParameters().get("location");
        if(location.size() != 1) throwError(BAD_REQUEST.getStatusCode(),"location missing. a location needs to be specified");
        List<String> timestamps = uriInfo.getQueryParameters().get("timestamp");
        if(timestamps.size() != 1) throwError(BAD_REQUEST.getStatusCode(),"timestamp missing. unix timestamp needs to be specified");
        String[] locationTokens = location.get(0).split(",");
        double lat = Double.parseDouble(locationTokens[0]);
        double lon = Double.parseDouble(locationTokens[1]);
        long timestamp = Long.parseLong(timestamps.get(0));
        String timeZoneId = getTimeZone(lat,lon);
        TimeZone timeZone = TimeZone.getTimeZone(timeZoneId);

        LocalTime localTime = getLocalTime(timeZone);

        com.graphhopper.timezone.api.TimeZone timeZoneResponse = new com.graphhopper.timezone.api.TimeZone(timeZoneId,localTime,timeZone.getOffset(timestamp) / 1000);
        return Response.status(Response.Status.OK).entity(timeZoneResponse).build();

    }

    private LocalTime getLocalTime(TimeZone timeZone){
        return LocalTime.now(timeZone.toZoneId());
    }

    private String getTimeZone(double lat, double lon){
        Point point = geometryFactory.createPoint(new Coordinate(lon,lat));
        List<Object> regions = quadtree.query(point.getEnvelopeInternal());
        for(Object o : regions){
            SimpleFeature feature = (SimpleFeature) o;
            Geometry geom = (Geometry) feature.getDefaultGeometry();
            if(point.within(geom)) {
                return (String)(feature.getAttribute("TZID"));
            }
        }
        throwError(BAD_REQUEST.getStatusCode(),"could not localize location " + lat + ", " + lon);
        return null;
    }

    private void throwError(int statusCode, String msg){
        ErrorMessage errorMessage = new ErrorMessage(statusCode, msg);
        throw new WebApplicationException(errorMessage.getMessage(), Response.status(statusCode)
                .entity(errorMessage)
                .type(MediaType.APPLICATION_JSON).
                        build());
    }

}
