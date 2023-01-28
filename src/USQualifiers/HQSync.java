package USQualifiers;

import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

import static USQualifiers.Util.intToLoc;
import static USQualifiers.Util.locToInt;

public class HQSync {

    private static final int ISLAND_ASSIGN = 47;

    public static void writeHQLocation(RobotController rc, MapLocation location, int hqID) throws GameActionException {
        if (hqID < 0 || hqID > GameConstants.MAX_STARTING_HEADQUARTERS - 1) {
            throw new IndexOutOfBoundsException("Hq num out of bounds");
        }

        if (!rc.canWriteSharedArray(0, 1)) {
            System.out.println("Could not write to shared array!");
            return;
        }

        rc.writeSharedArray(hqID, Integer.parseInt("0" + locToInt(location)));
    }

    public static MapLocation readHQLocation(RobotController rc, int hqID) throws GameActionException {
        if (hqID < 0 || hqID > GameConstants.MAX_STARTING_HEADQUARTERS - 1) {
            throw new IndexOutOfBoundsException("Hq num out of bounds");
        }

        return intToLoc(rc.readSharedArray(hqID) % 10000);
    }

    public static int readNumHQs(RobotController rc) throws GameActionException {
        int total = 0;
        for (int i = 0; i < GameConstants.MAX_STARTING_HEADQUARTERS; i++) {
            if (rc.readSharedArray(i) != 0) total++;
        }

        return total;
    }

    public static void assignIsland(RobotController rc, int hqNum, int assignment) throws GameActionException {
        if(!rc.canWriteSharedArray(0, 0)) {
            System.out.println("Can't write");
            return;
        }

        int read = rc.readSharedArray(ISLAND_ASSIGN);
        int mod = (int) Math.pow(10, hqNum);
        int write = (read / (mod * 10)) * (mod * 10) + read % mod + assignment * mod;
        rc.writeSharedArray(ISLAND_ASSIGN, write);
    }

    public static int readIsland(RobotController rc, int hqNum) throws GameActionException {
        int read = rc.readSharedArray(ISLAND_ASSIGN);
        int div = (int) Math.pow(10, hqNum);
        return read / div % 10;
    }
}
