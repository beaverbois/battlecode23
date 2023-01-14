package EnhancedRush;

import battlecode.common.*;

import static EnhancedRush.RobotPlayer.*;
import static EnhancedRush.RobotPlayer.directions;
import static EnhancedRush.RobotPlayer.rng;
import static Utilities.Util.*;

public class Launcher {

    static enum LauncherState {
        RUSHING,
        DEFENDING,
        REPORTING,
        ALPHA,
        BETA
    }
    static LauncherState lstate = LauncherState.RUSHING;

    static RobotInfo[] enemies;

    static MapLocation pos;

    static int lastAlphaDist = 100;

    static void run(RobotController rc) throws GameActionException {
        switch(lstate) {
            case RUSHING: rushing(rc); break;
            case REPORTING: reporting(rc); break;
            case DEFENDING: break; //No defending state atm
        }
    }

    private static void turnStart(RobotController rc) throws GameActionException {
        pos = rc.getLocation();

        for (int i = 0; i < allHQ.length; i++) {
            int read = rc.readSharedArray(allHQ.length + i + 1);
            if (read != 0 && read != locToInt(allOpposingHQ[i])) {
                if (locToInt(allOpposingHQ[i]) == 0) allOpposingHQ[i] = intToLoc(read);
                    //Doesn't account for the case of 3+ HQ where the
                    //robot has 2 new known HQ and another reports an HQ.
                else if (i < allHQ.length - 1) {
                    allOpposingHQ[i + 1] = allOpposingHQ[i];
                    allOpposingHQ[i] = intToLoc(read);
                }
            }
        }

        int radius = rc.getType().actionRadiusSquared;
        Team opponent = rc.getTeam().opponent();
        enemies = rc.senseNearbyRobots(radius, opponent);
    }

    private static void rushing(RobotController rc) throws GameActionException {
        turnStart(rc);

        scout(rc);

        attack(rc);

        if (rc.isMovementReady()) {
            if (locToInt(allOpposingHQ[0]) != 0) {
                //We know at least one enemy HQ exists.
                int count = 1;
                for (; count < allOpposingHQ.length; count++)
                    if (locToInt(allOpposingHQ[count]) == 0) break;
                MapLocation[] knownOppHQ = new MapLocation[count];
                System.arraycopy(allOpposingHQ, 0, knownOppHQ, 0, count);
                moveTowards(rc, closest(pos, knownOppHQ));
            } else if (rc.readSharedArray(20) != 0) {
                MapLocation target = intToLoc(rc.readSharedArray(20));
                moveTowards(rc, target);
            } else {
                moveRandom(rc);
            }
        }
    }

    private static void reporting(RobotController rc) throws GameActionException {
        if (rc.canWriteSharedArray(0, 0)) {
            //Update shared array with enemy HQ, currently may have problems with overwriting HQ.
            for (int i = 0; i < allHQ.length; i++)
                if (locToInt(allOpposingHQ[i]) != 0)
                    rc.writeSharedArray(allHQ.length + i + 1, locToInt(allOpposingHQ[i]));
            lstate = LauncherState.RUSHING;
        } else if (rc.isMovementReady()) {
            //Move towards closest headquarters.
            headquarters = closest(pos, allHQ);
            moveTowards(rc, headquarters);
        }
    }

    private static void beta(RobotController rc, int alphaID) throws GameActionException {
        turnStart(rc);

        attack(rc);

        RobotInfo alpha = rc.senseRobot(alphaID);
        MapLocation alphaPos = alpha.getLocation();
        int alphaDist = distance(pos, alphaPos);

        if(alphaDist > 4 || (alphaDist > lastAlphaDist && alphaDist > 2)) moveTowards(rc, alphaPos);
        else if(distance(pos, alphaPos) < 2 || (alphaDist < lastAlphaDist && alphaDist < 4)) moveAway(rc, alphaPos);
    }

    private static void alpha(RobotController rc) throws GameActionException {
        turnStart(rc);
    }

    private static void scout(RobotController rc) throws GameActionException {
        for (RobotInfo enemy : enemies) {
            if (enemy.getType() == RobotType.HEADQUARTERS) {
                int pos = locToInt(enemy.getLocation());
                int numHQ = allHQ.length;
                for (int i = 0; i < numHQ; i++) {
                    int val = locToInt(allOpposingHQ[i]);
                    if (val == pos) break;
                    if (val == 0) {
                        allOpposingHQ[i] = intToLoc(pos);
                        lstate = LauncherState.REPORTING;
                        break;
                    }
                }
            }
        }
    }

    private static void attack(RobotController rc) throws GameActionException {
        int targetPrio;
        if (rc.isActionReady() && enemies.length > 0) {
            // MapLocation toAttack = enemies[0].location;
            targetPrio = launcherPriority.indexOf(enemies[0].getType());
            MapLocation target = enemies[0].location;
            for (RobotInfo enemy : enemies) {
                RobotType etype = enemy.getType();
                if (launcherPriority.indexOf(etype) < targetPrio) {
                    targetPrio = launcherPriority.indexOf(etype);
                    target = enemy.location;
                }
            }
            if (rc.canAttack(target)) {
                rc.attack(target);
                if (rc.isMovementReady() && lstate == LauncherState.RUSHING) {
                    if (rc.canMove(directions[towards(pos, target)])) {
                        rc.move(directions[towards(pos, target)]);
                    }
                }
            }
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

    private static void moveAway(RobotController rc, MapLocation target) throws GameActionException {
        int dirIn = away(pos, target);
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
