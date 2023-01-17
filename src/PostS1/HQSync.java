package PostS1;

import battlecode.common.*;

public class HQSync {

    public static int hqMinIndex = 0;

//    public static MapLocation getHQLocation (RobotController rc, int hqNum) {
//        if (hqIndex < wellIndexMin || index > wellIndexMax)
//            throw new IndexOutOfBoundsException("HQ index out of bounds");
//        else if (rc.readSharedArray(index) == 0) {
//            return null;
//        }
//    }

    public static void addHQLocation(RobotController rc) throws GameActionException {
        int numHQ = getNumHQ(rc);

        if (numHQ > GameConstants.MAX_STARTING_HEADQUARTERS) {
            throw new GameActionException(GameActionExceptionType.CANT_DO_THAT, "Cannot add hq: max number reached");
        }

        int hqIndex = numHQ + hqMinIndex;

        if (rc.canWriteSharedArray(0,1)) {

        } else {
            System.out.println(rc.getID() + " Could not write to shared array!");
        }

    }

    public static MapLocation getClosestHQ(RobotController rc, MapLocation loc) {
        return null;
    }

    public static int getNumHQ(RobotController rc) {
        for (int i = 0; i < GameConstants.MAX_STARTING_HEADQUARTERS; i++) {
//            if (rc.readSharedArray()) //TODO: can read

        }
        return 0;
    }

}
