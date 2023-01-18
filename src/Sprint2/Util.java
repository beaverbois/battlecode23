package Sprint2;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

import java.util.*;

import static Sprint2.RobotPlayer.rng;
import static Sprint2.RobotPlayer.directions;

/**
 * Public utility class
 */
public class Util {

    /** Array containing all the possible movement directions. */

    public static final int LOC_MULTIPLIER = 100; // originally GameConstants.MAP_MAX_WIDTH

    private static final double MIN_MULTIPLIER = 4.0;

    public static MapLocation intToLoc(int raw) {
        return new MapLocation((raw - 1) / LOC_MULTIPLIER, (raw - 1) % LOC_MULTIPLIER);
    }

    // Maps (x,y) to "xy"
    public static int locToInt(MapLocation pos) {
        return pos.x * LOC_MULTIPLIER + pos.y + 1;
    }

    //Movement methods
    public static int distance(MapLocation pos, MapLocation target) {
        return Math.max(Math.abs(target.x - pos.x), Math.abs(target.y - pos.y));
    }

    //Causes some sketchy errors with rc.canMove() not stopping the robot from moving to an occupied space...
    public static double dist(MapLocation pos, MapLocation target) {
        int xDist = Math.abs(target.x - pos.x), yDist = Math.abs(target.y - pos.y);
        //Weighing the smaller distance much less.
        return Math.max(xDist, yDist) + Math.min(xDist, yDist) / MIN_MULTIPLIER;
    }

    // TODO: hashmap
    public static MapLocation closest(MapLocation pos, MapLocation... targets) {
        //Assuming at least 1 target, and ignoring terrain.
        if (targets.length == 0) {
            System.out.println("Calling closest with no targets.");
            return pos;
        }
        int minDistance = distance(pos, targets[0]);
        MapLocation close = targets[0];
        for (MapLocation target : targets) {
            if (distance(pos, target) < minDistance) {
                minDistance = distance(pos, target);
                close = target;
            }
        }
        return close;
    }

    // returns list of directions sorted by distance from a MapLocation to another for optimized path finding
    public static Direction[] closestDirections(MapLocation from, MapLocation to) {
        // Use a treemap with directions mapped to distances
        Map <Double, ArrayList<Direction>> map = new TreeMap<>();
        for (Direction direction : directions) {
            double dist = dist(from.add(direction), to);
            if (!map.containsKey(dist)) {
                ArrayList<Direction> dirs = new ArrayList<>();
                dirs.add(direction);
                map.put(dist, dirs);
            } else {
                ArrayList<Direction> dirs = map.get(dist);
                dirs.add(direction);
                map.replace(dist, dirs);
            }
        }

        // Assemble list of directions, randomizing the order of directions with the same distance
        ArrayList<Direction> output = new ArrayList<>();
        for (Map.Entry<Double, ArrayList<Direction>> entry : map.entrySet()) {
            ArrayList<Direction> dirs = entry.getValue();
            Collections.shuffle(dirs);
            output.addAll(dirs);
        }

        return output.toArray(new Direction[0]);
    }

    // TODO: Fix collisions in TreeMap, find shortest map implementation
    // returns list of directions sorted by distance from a MapLocation to another for optimized path finding, optionally including center
    public static Direction[] closestDirections(MapLocation from, MapLocation to, boolean includeCenter) {
        Direction[] directionList = directions;
        if (includeCenter) {
            directionList = Direction.allDirections();
        }

        // Use a treemap with directions mapped to distances
        Map <Double, ArrayList<Direction>> map = new TreeMap<>();
        for (Direction direction : directionList) {
            double dist = dist(from.add(direction), to);
            if (!map.containsKey(dist)) {
                ArrayList<Direction> dirs = new ArrayList<>();
                dirs.add(direction);
                map.put(dist, dirs);
            } else {
                ArrayList<Direction> dirs = map.get(dist);
                dirs.add(direction);
                map.replace(dist, dirs);
            }
        }

        // Assemble list of directions, randomizing the order of directions with the same distance
        ArrayList<Direction> output = new ArrayList<>();
        for (Map.Entry<Double, ArrayList<Direction>> entry : map.entrySet()) {
            ArrayList<Direction> dirs = entry.getValue();
            Collections.shuffle(dirs);
            output.addAll(dirs);
        }

        return output.toArray(new Direction[0]);
    }

    // returns list of nearby directions sorted by distance from a MapLocation for optimized path finding
    public static Direction[] farthestDirections(MapLocation from, MapLocation to) {
        Map <Double, Direction> map = new TreeMap<>();

        // rng prevents map conflicts with same distances
        for (Direction direction : directions) {
            map.put(-1 * dist((from.add(direction)), to) + rng.nextDouble() / 100.0, direction);
        }

        return map.values().toArray(new Direction[0]);
    }

    // TODO: Fix collisions in TreeMap
    // returns a list of sorted MapLocations distances to a fixed MapLocation within an action radius of a robot
    public static MapLocation[] closestLocationsInActionRadius(RobotController rc, MapLocation from, MapLocation to) throws GameActionException {
        Map<Double, MapLocation> map = new TreeMap<>();
        for (MapLocation loc : rc.getAllLocationsWithinRadiusSquared(from, rc.getType().actionRadiusSquared)) {
            map.put(dist(loc, to), loc);
        }

        return map.values().toArray(new MapLocation[0]);
    }

    public static MapLocation[] farthestLocationsInActionRadius(RobotController rc, MapLocation from, MapLocation to) throws GameActionException {
        Map<Integer, MapLocation> map = new TreeMap<>();
        for (MapLocation loc : rc.getAllLocationsWithinRadiusSquared(from, rc.getType().actionRadiusSquared)) {
            map.put(-1 * distance(loc, to), loc);
        }

        return map.values().toArray(new MapLocation[0]);
    }


    public static void moveTowards(RobotController rc, MapLocation target) throws GameActionException {
        if(rc.isMovementReady()) {
            MapLocation pos = rc.getLocation();
            Direction[] closest = closestDirections(pos, target);

            for(int i = 0; i < closest.length; i++) {
                Direction dir = closest[i];
                if(rc.canMove(dir)) {
                    rc.move(dir);
                    //Accounts multiple movements
                    if(!rc.isMovementReady()) break;
                    closest = closestDirections(rc.getLocation(), target);
                    i = 0;
                }
            }
        }
    }

    public static void moveAway(RobotController rc, MapLocation target) throws GameActionException {
        //May want to switch this to randomize between the three "away" directions.
        MapLocation pos = rc.getLocation();

        Direction[] closest = farthestDirections(pos, target);
        if(rc.isMovementReady()) {
            for(int i = 0; i < closest.length; i++) {
                Direction dir = closest[i];
                if(rc.canMove(dir)) {
                    rc.move(dir);
                    //Accounts for multiple movements
                    if(!rc.isMovementReady()) break;
                    closest = closestDirections(rc.getLocation(), target);
                    i = 0;
                }
            }
        }
    }
}