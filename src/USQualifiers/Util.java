package USQualifiers;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

import java.util.Arrays;

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
        double minDistance = dist(pos, targets[0]);
        MapLocation close = targets[0];
        for (MapLocation target : targets) {
            if (dist(pos, target) < minDistance) {
                minDistance = dist(pos, target);
                close = target;
            }
        }
        return close;
    }

    //     returns the closest available location to a robot around an origin location
    public static MapLocation closestAvailableLocationTowardsRobot(RobotController rc, MapLocation origin) throws GameActionException {
        // if we can't sense the origin or if we can and it's already free
        if (!rc.canSenseLocation(origin) || isLocationFree(rc, origin)) {
            return origin;
        }

        // get the location closest to us around the origin
        Direction closestDir = origin.directionTo(rc.getLocation());
        MapLocation closestSquare = origin.add(closestDir);

        // return closest square to us if we are too far to sense the origin or if closest square is already free
        if (isLocationFree(rc, closestSquare)) {
            return closestSquare;
        }

        Direction rightDir = closestDir.rotateRight();
        Direction leftDir = closestDir.rotateLeft();

        for (int i = 0; i < 3; i++) {
            if (isLocationFree(rc, origin.add(rightDir))) {
                return origin.add(rightDir);
            }

            if (isLocationFree(rc, origin.add(leftDir))) {
                return origin.add(leftDir);
            }

            rightDir = rightDir.rotateRight();
            leftDir = leftDir.rotateLeft();
        }

        if (isLocationFree(rc, origin.add(rightDir))) {
            return origin.add(rightDir);
        }

//        System.out.println("Could not find an available square around " + origin.toString() + " !");
        return null;
    }

    // returns true if location can be sensed, location is free of robots, location is passable, and location doesn't contain a current
    public static boolean isLocationFree(RobotController rc, MapLocation location) throws GameActionException {
        return rc.canSenseLocation(location) && !rc.isLocationOccupied(location) && rc.sensePassability(location) && rc.senseMapInfo(location).getCurrentDirection() == Direction.CENTER;
    }

    // returns true if there is no current (or current points in direction) in the direction specified around rc
    public static boolean senseCurrent(RobotController rc, Direction direction) throws GameActionException {
        MapLocation location = rc.getLocation().add(direction);
        Direction currentDir = rc.senseMapInfo(location).getCurrentDirection();

        return currentDir == Direction.CENTER || currentDir == direction;
    }

    // returns the closest available direction around a robot towards a target location
    public static Direction closestAvailableDirectionAroundRobot(RobotController rc, MapLocation target) throws GameActionException {
        Direction closestDir = rc.getLocation().directionTo(target);
        if (rc.canMove(closestDir) && senseCurrent(rc, closestDir)) {
            return closestDir;
        }

        Direction rightDir = closestDir.rotateRight();
        Direction leftDir = closestDir.rotateLeft();

        for (int i = 0; i < 3; i++) {
            if (rc.canMove(rightDir) && senseCurrent(rc, rightDir)) {
                return rightDir;
            }

            if (rc.canMove(leftDir) && senseCurrent(rc, leftDir)) {
                return leftDir;
            }

            rightDir = rightDir.rotateRight();
            leftDir = leftDir.rotateLeft();
        }

        if (rc.canMove(rightDir) && senseCurrent(rc, rightDir)) {
            return rightDir;
        }

//        System.out.println("Could not find an available direction!");
        return null;
    }

    // returns the farthest available direction around a robot from a target location
    public static Direction farthestAvailableDirectionAroundRobot(RobotController rc, MapLocation target) {
        Direction farthestDir = target.directionTo(rc.getLocation());
        if (rc.canMove(farthestDir)) {
            return farthestDir;
        }

        Direction rightDir = farthestDir.rotateRight();
        Direction leftDir = farthestDir.rotateLeft();

        for (int i = 0; i < 3; i++) {
            if (rc.canMove(rightDir)) {
                return rightDir;
            }

            if (rc.canMove(leftDir)) {
                return leftDir;
            }

            rightDir = rightDir.rotateRight();
            leftDir = leftDir.rotateLeft();
        }

        if (rc.canMove(rightDir)) {
            return rightDir;
        }

//        System.out.println("Could not find an available direction!");
        return null;
    }

    // returns a list of sorted MapLocations distances to a fixed MapLocation within an action radius of a robot
    public static MapLocation[] closestLocationsInActionRadius(RobotController rc, MapLocation from, MapLocation to) throws GameActionException {
        MapLocation[] locs = rc.getAllLocationsWithinRadiusSquared(from, rc.getType().actionRadiusSquared);
        int[] close = new int[locs.length];
        for (int i = 0; i < locs.length; i++) {
            close[i] = 10000 * distance(locs[i], to) + locToInt(locs[i]);
        }

        Arrays.sort(locs);

        for (int i = 0; i < close.length; i++) {
            locs[i] = intToLoc(close[i] % 10000);
        }

        return locs;
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


    // returns true if we successfully moved/are already adjacent to target
    public static boolean moveTowards(RobotController rc, MapLocation target) throws GameActionException {
        if (rc.getLocation().isAdjacentTo(target)) return true;

        Direction closestDir = closestAvailableDirectionAroundRobot(rc, target);
        if (closestDir != null) {
            rc.move(closestDir);
            if (rc.isMovementReady()) moveTowards(rc, target);

            return true;
        }
        return false;
    }

    // returns true if we succesfully moved
    public static boolean moveAway(RobotController rc, MapLocation target) throws GameActionException {
        Direction farthestDir = farthestAvailableDirectionAroundRobot(rc, target);
        if (farthestDir != null) {
            rc.move(farthestDir);
            if (rc.isMovementReady()) moveAway(rc, target);

            return true;
        }
        return false;
    }
}