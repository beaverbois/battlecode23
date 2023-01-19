package Sprint2;

import battlecode.common.*;

import static Sprint2.Util.intToLoc;
import static Sprint2.Util.locToInt;

public class HQSync {

    public static void writeHQLocation(RobotController rc, MapLocation location, int hqNum) throws GameActionException {
        if (hqNum < 0 || hqNum > GameConstants.MAX_STARTING_HEADQUARTERS - 1) {
            throw new IndexOutOfBoundsException("Hq num out of bounds");
        }

        if (!rc.canWriteSharedArray(0, 1)) {
            System.out.println("Could not write to shared array!");
            return;
        }

        rc.writeSharedArray(hqNum, Integer.parseInt("0" + locToInt(location)));
    }

    public static MapLocation readHQLocation(RobotController rc, int hqNum) throws GameActionException {
        if (hqNum < 0 || hqNum > GameConstants.MAX_STARTING_HEADQUARTERS - 1) {
            throw new IndexOutOfBoundsException("Hq num out of bounds");
        }

        return intToLoc(rc.readSharedArray(hqNum) % 10000);
    }

    public static int readNumHQs(RobotController rc) throws GameActionException {
        int total = 0;
        for (int i = 0; i < GameConstants.MAX_STARTING_HEADQUARTERS; i++) {
            if (rc.readSharedArray(i) != 0) total++;
        }

        return total;
    }
}
