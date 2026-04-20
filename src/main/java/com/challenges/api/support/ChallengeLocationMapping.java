package com.challenges.api.support;

import com.challenges.api.web.dto.ChallengeLocationDto;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

public final class ChallengeLocationMapping {

	private static final GeometryFactory GEOMETRY_FACTORY =
			new GeometryFactory(new PrecisionModel(), 4326);

	private ChallengeLocationMapping() {}

	/** Persists WGS-84: JTS x = longitude, y = latitude. */
	public static Point toPoint(ChallengeLocationDto dto) {
		double lat = dto.latitude();
		double lon = dto.longitude();
		return GEOMETRY_FACTORY.createPoint(new Coordinate(lon, lat));
	}

	public static ChallengeLocationDto fromPoint(Point point) {
		if (point == null || point.isEmpty()) {
			return null;
		}
		double lon = point.getX();
		double lat = point.getY();
		return new ChallengeLocationDto(lat, lon);
	}
}
