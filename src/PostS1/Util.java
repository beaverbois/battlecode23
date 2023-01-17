package PostS1;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.world.RobotControllerImpl;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static PostS1.RobotPlayer.rng;
import static PostS1.RobotPlayer.directions;

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

    //Causes some sketchy errors with rc.canMove() not stopping the robot from moving to an occupied space...
    public static double dist(MapLocation pos, MapLocation target) {
        return Math.sqrt(Math.pow(target.x - pos.x, 2) + Math.pow(target.y - pos.y, 2));
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

    // returns list of nearby directions sorted by distance to a MapLocation for optimized path finding
    public static Direction[] closestDirectionsTo (MapLocation pos, MapLocation target) {
        Map <Double, Direction> map = new HashMap<>();
        for (Direction direction : directions) {
            //RNG ensures there are unique keys
            map.put(dist((pos.add(direction)), target) + rng.nextDouble() / 100.0, direction);
        }

        //It just works I guess
        return new TreeMap<>(map).values().toArray(new Direction[0]);
    }

    // returns list of nearby directions sorted by distance from a MapLocation for optimized path finding
    public static Direction[] farthestDirectionsFrom (MapLocation pos, MapLocation target) {
        Map <Double, Direction> map = new HashMap<>();
        for (Direction direction : directions) {
            map.put(-1 * distance((pos.add(direction)), target) + rng.nextDouble(), direction);
        }

        return new TreeMap<>(map).values().toArray(new Direction[0]);
    }

    public static void moveTowards(RobotController rc, MapLocation target) throws GameActionException {
        MapLocation pos = rc.getLocation();

        Direction[] closest = closestDirectionsTo(pos, target);
        if(rc.isMovementReady()) {
            for(int i = 0; i < closest.length; i++) {
                Direction dir = closest[i];
                if(rc.canMove(dir)) {
                    rc.move(dir);
                    //Accounts multiple movements
                    if(!rc.isMovementReady()) break;
                    closest = closestDirectionsTo(rc.getLocation(), target);
                    i = 0;
                }
            }
        }
    }

    public static void moveAway(RobotController rc, MapLocation target) throws GameActionException {
        //May want to switch this to randomize between the three "away" directions.
        MapLocation pos = rc.getLocation();

        Direction[] closest = farthestDirectionsFrom(pos, target);
        if(rc.isMovementReady()) {
            for(int i = 0; i < closest.length; i++) {
                Direction dir = closest[i];
                if(rc.canMove(dir)) {
                    rc.move(dir);
                    //Accounts for multiple movements
                    if(!rc.isMovementReady()) break;
                    closest = closestDirectionsTo(rc.getLocation(), target);
                    i = 0;
                }
            }
        }
    }
}