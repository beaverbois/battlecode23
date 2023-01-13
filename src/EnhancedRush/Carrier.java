package EnhancedRush;

import battlecode.common.*;

import static EnhancedRush.RobotPlayer.*;
import static Util.Util.*;

public class Carrier {

    static enum CarrierState {
        FARMING,
        RETURNING,
        SCOUTING,
        REPORTING
    }
    static CarrierState cstate = CarrierState.FARMING;
    static MapLocation enemyLoc;
    static MapLocation pos;

    static int weight = 0;

    static void run(RobotController rc) throws GameActionException {
        turnStart(rc);

        switch(cstate) {
            case FARMING: farming(rc);
            break;
            case SCOUTING: scouting(rc);
            break;
            case REPORTING: reporting(rc);
            break;
            case RETURNING: returning(rc);
            break;
        }
    }

    private static void turnStart(RobotController rc) throws GameActionException {
        //Start by defining weight and position
        weight = rc.getResourceAmount(ResourceType.ADAMANTIUM) +
                rc.getResourceAmount(ResourceType.MANA) +
                rc.getResourceAmount(ResourceType.ELIXIR);
        pos = rc.getLocation();

        rc.setIndicatorString(cstate.toString());
    }

    private static void reporting(RobotController rc) throws GameActionException {
        for(int i = 0; i < allHQ.length; i++) {
            int read = rc.readSharedArray(allHQ.length + i + 1);
            if(read != 0 && read != locToInt(allOpposingHQ[i])) {
                if(locToInt(allOpposingHQ[i]) == 0) allOpposingHQ[i] = intToLoc(read);
                    //Doesn't account for the case of 3+ HQ where the
                    //robot has 2 new known HQ and another reports an HQ.
                else if(i < allHQ.length - 1) {
                    allOpposingHQ[i+1] = allOpposingHQ[i];
                    allOpposingHQ[i] = intToLoc(read);
                }
            }
        }

        if(rc.canWriteSharedArray(0, 0)) {
            if(enemyLoc != null) {
                //For now trying "dumb" writing, where it will override.
                rc.writeSharedArray(20, locToInt(enemyLoc));
                enemyLoc = null;
            }

            //Update shared array with enemy HQ, currently may have problems with overwriting HQ.
            for(int i = 0; i < allHQ.length; i++)
                if(locToInt(allOpposingHQ[i]) != 0)
                    rc.writeSharedArray(allHQ.length+i+1, locToInt(allOpposingHQ[i]));
            if(weight != 0) cstate = CarrierState.RETURNING;
            else cstate = CarrierState.FARMING;
        }

        if(rc.isMovementReady()) {
            moveTowards(rc, headquarters);
        }
    }

    private static void scouting(RobotController rc) throws GameActionException {
        senseEnemies(rc);

        if(cstate == CarrierState.SCOUTING && rc.isMovementReady()) {
            int dist = distance(pos, corner);
            moveAway(rc, corner);
            if(distance(pos, headquarters) < dist) corner = pos;
        }
    }

    private static void farming(RobotController rc) throws GameActionException {
        senseEnemies(rc);

        if(weight == 40) cstate = CarrierState.RETURNING;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                MapLocation wellLocation = new MapLocation(pos.x + dx, pos.y + dy);
                if (rc.canCollectResource(wellLocation, -1)) {
                    rc.collectResource(wellLocation, -1);
                    return;
                }
            }
        }

        // If we can see a well, move towards it
        if(rc.isMovementReady()) {
            WellInfo[] wells = rc.senseNearbyWells();
            MapLocation[] wellLoc = new MapLocation[wells.length];
            if(wells.length != 0) {
                for (int i = 0; i < wells.length; i++) wellLoc[i] = wells[i].getMapLocation();
                moveTowards(rc, closest(pos, wellLoc));
            } else {
                moveAway(rc, corner);
            }
        }
    }

    private static void returning(RobotController rc) throws GameActionException {
        if (headquarters.isAdjacentTo(pos) && weight > 0) {
            if (rc.canTransferResource(headquarters, ResourceType.ADAMANTIUM, 1))
                rc.transferResource(headquarters, ResourceType.ADAMANTIUM, rc.getResourceAmount(ResourceType.ADAMANTIUM));
            if (rc.canTransferResource(headquarters, ResourceType.MANA, 1))
                rc.transferResource(headquarters, ResourceType.MANA, rc.getResourceAmount(ResourceType.MANA));
            if (rc.canTransferResource(headquarters, ResourceType.ELIXIR, 1))
                rc.transferResource(headquarters, ResourceType.ELIXIR, rc.getResourceAmount(ResourceType.ELIXIR));
            return;
        }

        if (weight == 0) cstate = CarrierState.FARMING;
        else if(rc.isMovementReady()) {
            moveTowards(rc, headquarters);
        }
    }

    private static void senseEnemies(RobotController rc) throws GameActionException {
        int radius = rc.getType().actionRadiusSquared;
        Team opponent = rc.getTeam().opponent();
        RobotInfo[] enemies = rc.senseNearbyRobots(radius, opponent);
        for (RobotInfo enemy : enemies) {
            if (enemy.getType() == RobotType.HEADQUARTERS) {
                int pos = locToInt(enemy.getLocation());
                int numHQ = allHQ.length;
                for (int i = 0; i < numHQ; i++) {
                    int val = locToInt(allOpposingHQ[i]);
                    if (val == pos) break;
                    if (val == 0) {
                        allOpposingHQ[i] = intToLoc(pos);
                        cstate = CarrierState.REPORTING;
                        return;
                    }
                }
            } else {
                enemyLoc = enemy.getLocation();
                cstate = CarrierState.REPORTING;
            }
        }
    }

    private static void moveAway(RobotController rc, MapLocation from) throws GameActionException {
        int dirIn = away(pos, from);
        if(dirIn == -1) {
            moveRandom(rc);
            return;
        }
        int randInt = rng.nextInt(3);
        Direction dir = directions[(dirIn + (randInt - 1) + directions.length) % directions.length];
        if (rc.canMove(dir)) rc.move(dir);
        else if (rc.canMove(directions[(dirIn + (randInt % 2) + directions.length - 1) % directions.length]))
            rc.move(directions[(dirIn + (randInt % 2) + directions.length - 1) % directions.length]);
        else if(rc.canMove(directions[(dirIn + (randInt + 1 % 2) + directions.length - 1) % directions.length]))
            rc.move(directions[(dirIn + (randInt + 1 % 2) + directions.length - 1) % directions.length]);
        else {
            corner = pos;
            moveRandom(rc);
        }
    }

    private static void moveTowards(RobotController rc, MapLocation target) throws GameActionException {
        int dirIn = towards(pos, target);
        if(dirIn == -1) {
            moveRandom(rc);
            return;
        }
        Direction dir = directions[dirIn];
        if (rc.canMove(dir)) rc.move(dir);
        else if (rc.canMove(directions[(dirIn + 1) % directions.length]))
            rc.move(directions[(dirIn + 1) % directions.length]);
        else if(rc.canMove(directions[((dirIn - 1) + directions.length - 1) % directions.length]))
            rc.move(directions[((dirIn - 1) + directions.length - 1) % directions.length]);
        else {
            moveRandom(rc);
        }
    }

    private static void moveRandom(RobotController rc) throws GameActionException {
        int randDir = rng.nextInt(directions.length);
        Direction dir = directions[randDir++ % directions.length];
        for (int i = 0; i < directions.length && !rc.canMove(dir); i++) {
            dir = directions[randDir++ % directions.length];
        }
        if (rc.canMove(dir)) rc.move(dir);
    }
}
