package Sprint2;

import battlecode.common.*;
import javafx.util.Pair;

import java.util.*;

import static Sprint2.RobotPlayer.rng;
import static Sprint2.RobotPlayer.directions;

/**
 * Public utility class
 */
public class Util {

    /**
     * Array containing all the possible movement directions.
     */

    public static final int LOC_MULTIPLIER = 100; // originally GameConstants.MAP_MAX_WIDTH

    private static final double MIN_MULTIPLIER = 4.0;

    public static MapLocation intToLoc(int raw) {
        return new MapLocation((raw - 1) / LOC_MULTIPLIER, (raw - 1) % LOC_MULTIPLIER);
    }

    // Maps (x,y) to "xy"
    public static int locToInt(MapLocation pos) {
        return pos.x * LOC_MULTIPLIER + pos.y + 1;
    }

    public static boolean withinSquaredRadius(MapLocation pos, MapLocation other, int radius) {
        double xDist = other.x - pos.x, yDist = other.y - pos.y;
        return xDist * xDist + yDist * yDist <= radius;
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

//     returns list of directions sorted by distance from a MapLocation to another for optimized path finding
    public static Direction[] closestDirections(RobotController rc, MapLocation from, MapLocation to) {
        ArrayList<Direction> openDirections = new ArrayList<>();
        Direction closest = from.directionTo(to);
        if (rc.canMove(closest)) {
            openDirections.add(closest);
        }

        Direction nextDir = closest.rotateRight();
        Direction delta = from.add(nextDir).directionTo(from.add(closest));
        for (int i = 0; i < 3; i++) {
            if (rc.canMove(nextDir)) {
                openDirections.add(nextDir);
            }

            Direction oppositeDir = from.directionTo(from.add(nextDir).add(delta).add(delta));

            if (rc.canMove(oppositeDir)) {
                openDirections.add(oppositeDir);
            }

            nextDir = nextDir.rotateRight();
        }

         if (rc.canMove(nextDir)) {
             openDirections.add(nextDir);
         }

         return openDirections.toArray(new Direction[0]);
    }

    public static Direction[] closeDirections(RobotController rc, MapLocation from, MapLocation to) {
        double[] close = new double[directions.length];
        for (int i = 0; i < directions.length; i++) {
            double rand = rng.nextDouble();
            double distance = dist((from.add(directions[i])), to) + rand;
            close[i] = distance + (i * 100);
        }

        //Sort the array
        for (int i = 1; i < directions.length; i++) {
            for (int j = i; j > 0; j--) {
                if (close[j] % 100 < close[j - 1] % 100) {
                    double temp = close[j - 1];
                    close[j - 1] = close[j];
                    close[j] = temp;
                } else break;
            }
        }

        //Add to directions array
        Direction[] dir = new Direction[directions.length];

        for (int i = 0; i < directions.length; i++) {
            dir[i] = directions[(int) (close[i] / 100)];
        }

        return dir;
    }


    // returns list of nearby directions sorted by distance from a MapLocation for optimized path finding
    public static Direction[] farthestDirections(MapLocation from, MapLocation to) {
        double[] close = new double[directions.length];
        for (int i = 0; i < directions.length; i++) {
            double rand = rng.nextDouble();
            double distance = dist((from.add(directions[i])), to) + rand;
            close[i] = distance + i * 100;
        }

        //Sort the array
        for (int i = 1; i < directions.length; i++) {
            for (int j = i; j > 0; j--) {
                if (close[j] % 100 > close[j - 1] % 100) {
                    double temp = close[j - 1];
                    close[j - 1] = close[j];
                    close[j] = temp;
                } else break;
            }
        }

        //Add to directions array
        Direction[] dir = new Direction[directions.length];

        for (int i = 0; i < directions.length; i++) {
            dir[i] = directions[(int) close[i] / 100];
        }

        return dir;
    }

    // TODO: Fix collisions in TreeMap
    // returns a list of sorted MapLocations distances to a fixed MapLocation within an action radius of a robot
    public static MapLocation[] closestLocationsInActionRadius(RobotController rc, MapLocation from, MapLocation to) throws GameActionException {
        MapLocation[] locs = rc.getAllLocationsWithinRadiusSquared(from, rc.getType().actionRadiusSquared);
        int[] close = new int[locs.length];
        for (int i = 0; i < locs.length; i++) {
            close[i] = 10000 * distance(locs[i], to) + locToInt(locs[i]);
        }

        Arrays.sort(locs);

        for(int i = 0; i < close.length; i++) {
            locs[i] = intToLoc(close[i] % 10000);
        }

        return locs;
//        Map<Double, MapLocation> map = new TreeMap<>();
//        for (MapLocation loc : rc.getAllLocationsWithinRadiusSquared(from, rc.getType().actionRadiusSquared)) {
//            map.put(dist(loc, to), loc);
//        }
//
//        return map.values().toArray(new MapLocation[0]);
    }

    public static MapLocation[] farthestLocationsInActionRadius(RobotController rc, MapLocation from, MapLocation to) throws GameActionException {
        MapLocation[] locs = rc.getAllLocationsWithinRadiusSquared(from, rc.getType().actionRadiusSquared);
        int[] close = new int[locs.length];
        for (int i = 0; i < locs.length; i++) {
            close[i] = -1 * (10000 * distance(locs[i], to) + locToInt(locs[i]));
        }

        Arrays.sort(close);

        for (int i = 0; i < close.length; i++) {
            locs[i] = intToLoc(close[i] * -1 % 10000);
        }

        return locs;
    }


    public static void moveTowards(RobotController rc, MapLocation target) throws GameActionException {
        if (rc.isMovementReady()) {
            MapLocation pos = rc.getLocation();
            Direction[] closest = closestDirections(rc, pos, target);

            for (int i = 0; i < closest.length; i++) {
                Direction dir = closest[i];
                if (rc.canMove(dir)) {
                    rc.move(dir);
                    //Accounts multiple movements
                    if (!rc.isMovementReady()) break;
                    closest = closestDirections(rc, rc.getLocation(), target);
                    i = 0;
                }
            }
        }
    }

    public static void moveAway(RobotController rc, MapLocation target) throws GameActionException {
        //May want to switch this to randomize between the three "away" directions.
        MapLocation pos = rc.getLocation();

        Direction[] closest = farthestDirections(pos, target);
        if (rc.isMovementReady()) {
            for (int i = 0; i < closest.length; i++) {
                Direction dir = closest[i];
                if (rc.canMove(dir)) {
                    rc.move(dir);
                    //Accounts for multiple movements
                    if (!rc.isMovementReady()) break;
                    closest = farthestDirections(rc.getLocation(), target);
                    i = 0;
                }
            }
        }
    }
}