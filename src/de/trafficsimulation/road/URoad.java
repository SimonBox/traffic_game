package de.trafficsimulation.road;

import java.util.ArrayList;
import java.util.List;

/**
 * A "U" shape on its side with counter-clockwise flow when drawn with the
 * positive y axis pointing down (display coordinates).
 */
public class URoad extends CompoundRoad {
  public URoad(int numLanes, double laneWidthMeters,
      double leftMeters, double topMeters,
      double curveRadiusMeters, double straightMeters)
  {
    super(makeRoads(numLanes, laneWidthMeters,
      leftMeters, topMeters, curveRadiusMeters, straightMeters));
  }

  private static List<RoadBase> makeRoads(int numLanes,
      double laneWidthMeters, double leftMeters, double bottomMeters,
      double curveRadiusMeters, double straightMeters) {
    
    double roadWidth = numLanes * laneWidthMeters;
    double rightMeters = leftMeters + curveRadiusMeters + straightMeters;
    double topMeters = bottomMeters + 2*curveRadiusMeters + 2*roadWidth;
    
    // input road
    double inputStartX = rightMeters;
    double inputY = bottomMeters + roadWidth;
    double inputEndX = leftMeters + curveRadiusMeters;
    StraightRoad input = new StraightRoad(numLanes, laneWidthMeters,
        inputStartX, inputY, inputEndX, inputY);
    
    // output road
    double outputStartX = inputEndX;
    double outputY = topMeters - roadWidth;
    double outputEndX = inputStartX;
    StraightRoad output = new StraightRoad(numLanes, laneWidthMeters,
        outputStartX, outputY, outputEndX, outputY);
    
    // curve
    double centerY = inputY + curveRadiusMeters;
    ArcRoad arc = new ArcRoad(numLanes, laneWidthMeters, inputEndX, centerY,
        curveRadiusMeters, 90, 180);
    
    List<RoadBase> roads = new ArrayList<RoadBase>();
    roads.add(input);
    roads.add(arc);
    roads.add(output);
    return roads;
  }
  
  public StraightRoad getInputStraightRoad() {
    return (StraightRoad)getRoads().get(0);
  }
  
  public ArcRoad getCurveRoad() {
    return (ArcRoad)getRoads().get(1);
  }
  
  public StraightRoad getOutputStraightRoad() {
    return (StraightRoad)getRoads().get(2);
  }
}
