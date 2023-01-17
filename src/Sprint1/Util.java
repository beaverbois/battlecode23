package Sprint1;

import battlecode.common.Direction;
import battlecode.common.MapLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static Sprint1.RobotPlayer.directions;

/**
 * Public utility class
 */
public class Util {

    /** Array containing all the possible movement directions. */

    public static final int LOC_MULTIPLIER = 100; // originally GameConstants.MAP_MAX_WIDTH

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

    // TODO: remove
    public static int away(MapLocation pos, MapLocation target) {
        //Returns the INDEX in directions of the direction away from target.
        if (pos == target) return -1;

        int xgap, ygap;

        xgap = target.x - pos.x;
        ygap = target.y - pos.y;

        if (xgap == 0) return ygap > 0 ? 4 : 0;
        if (ygap == 0) return xgap > 0 ? 6 : 2;
        if (xgap > 0) return ygap > 0 ? 5 : 7;
        return ygap > 0 ? 1 : 3;
    }

    // TODO: remove
    public static int towards(MapLocation pos, MapLocation target) {
        //Returns the INDEX in directions of the direction away from target.
        if (pos == target) return -1;

        int xgap, ygap;

        xgap = target.x - pos.x;
        ygap = target.y - pos.y;

        if (xgap == 0) return ygap > 0 ? 0 : 4;
        if (ygap == 0) return xgap > 0 ? 2 : 6;
        if (xgap > 0) return ygap > 0 ? 1 : 3;
        return ygap > 0 ? 7 : 5;
    }

//    public static void moveTowards(RobotController rc, MapLocation to) throws GameActionException {
//        for (Direction dir : closestDirectionsTo(rc.getLocation(), to)) {
//            if (rc.canMove(dir)) {
//                rc.move(dir);
//                break;
//            }
//        }
//    }
//
//    public static void moveAway(RobotController rc, MapLocation from) throws GameActionException {
//        for (Direction dir : farthestDirectionsFrom(rc.getLocation(), from)) {
//            if (rc.canMove(dir)) {
//                rc.move(dir);
//                break;
//            }
//        }
//    }

    // returns list of nearby directions sorted by distance to a MapLocation for optimized path finding
    public static Direction[] closestDirectionsTo (MapLocation pos, MapLocation target) {
        Map <Integer, Direction> map = new HashMap<>();
        for (Direction direction : directions) {
            map.put(distance((pos.add(direction)), target), direction);
        }

        return new TreeMap<Integer, Direction>(map).values().toArray(new Direction[0]);
    }

    // returns list of nearby directions sorted by distance from a MapLocation for optimized path finding
    public static Direction[] farthestDirectionsFrom (MapLocation pos, MapLocation target) {
        Map <Integer, Direction> map = new HashMap<>();
        for (Direction direction : directions) {
            map.put(-1 * distance((pos.add(direction)), target), direction);
        }

        return new TreeMap<Integer, Direction>(map).values().toArray(new Direction[0]);
    }
}