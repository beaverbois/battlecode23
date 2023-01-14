package AmpMasterServant;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.Arrays;

import static RushWithAnchors.RobotPlayer.*;
import static Utilities.Util.*;

public class Launcher {

    static enum LauncherState {
        RUSHING,
        DEFENDING,
        REPORTING,
        BETA
    }
    static LauncherState lstate = LauncherState.DEFENDING;

    static final ArrayList<RobotType> launcherPriority = new ArrayList<RobotType>(Arrays.asList(
            RobotType.DESTABILIZER,
            RobotType.LAUNCHER,
            RobotType.BOOSTER,
            RobotType.CARRIER,
            RobotType.AMPLIFIER,
            RobotType.HEADQUARTERS
    ));

    static RobotInfo[] enemies;

    static MapLocation pos;

    static int lastAlphaDist = 100;

    static int alphaIndex = 24;
    static int alphaTargetIndex = 0;

    static void run(RobotController rc) throws GameActionException {
        rc.setIndicatorString(lstate.toString());

        switch(lstate) {
            case RUSHING: rushing(rc); break;
            case REPORTING: reporting(rc); break;
            case BETA: beta(rc); break;
            case DEFENDING: defending(rc); break;
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

        int radius = rc.getType().visionRadiusSquared;
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
            for(int i = 0; i < allHQ.length; i++) {
                int read = rc.readSharedArray(4 + i);
                if (read == 0 && locToInt(allOpposingHQ[i]) != 0) {
                    rc.writeSharedArray(4 + i, locToInt(allOpposingHQ[i]));
                    writeToEmptyTarget(rc, locToInt(allOpposingHQ[i]), true);
                }
            }
            lstate = LauncherState.RUSHING;
        } else if (rc.isMovementReady()) {
            //Move towards closest headquarters.
            headquarters = closest(pos, allHQ);
            moveTowards(rc, headquarters);
        }
    }

    private static void beta(RobotController rc) throws GameActionException {
        if(rc.readSharedArray(alphaIndex) == 0) {
            //Just gonna hope that we don't have an infinite fake alpha. Will fix this later, maybe not before sprint 1.
            lstate = LauncherState.RUSHING;
            rushing(rc);
            return;
        }

        turnStart(rc);

        int alphaID = rc.readSharedArray(alphaTargetIndex);

        if(alphaTargetIndex == 0) {
            for (int i = 32; i < 43; i += 2) {
                if(rc.readSharedArray(i) == alphaID) {
                    alphaTargetIndex = i;
                    break;
                }
            }
        }

        MapLocation alphaPos = intToLoc(rc.readSharedArray(alphaIndex + 1));
        int alphaDist = distance(pos, alphaPos);
        MapLocation inFront = alphaTargetIndex != 0 ?
                alphaPos.add(directions[towards(alphaPos, intToLoc(rc.readSharedArray(alphaTargetIndex)))])
                .add(directions[towards(alphaPos, intToLoc(rc.readSharedArray(alphaTargetIndex)))])
                .add(directions[towards(alphaPos, intToLoc(rc.readSharedArray(alphaTargetIndex)))])
                : alphaPos;

        if(alphaDist > 3 || (alphaDist > lastAlphaDist && alphaDist > 2)) moveTowards(rc, inFront);
        else if(distance(pos, alphaPos) < 1 || (alphaDist < lastAlphaDist && alphaDist < 2)) moveAway(rc, inFront);

        attack(rc);
    }

    private static void defending(RobotController rc) throws GameActionException {
        turnStart(rc);

        attack(rc);

        if(!rc.isMovementReady()) return;

        for(int i = 0; i < allHQ.length; i++) {
            if(rc.readSharedArray(i) / 10000 == 1) {
                //HQ under attack
                moveTowards(rc, intToLoc(rc.readSharedArray(i) % 10000));
                return;
            }
        }

        int radius = rc.getType().actionRadiusSquared;
        Team myteam = rc.getTeam();
        RobotInfo[] allies = rc.senseNearbyRobots(radius, myteam);

        int farthest = 0;
        MapLocation far = null;

        for(RobotInfo ally : allies) {
            if(ally.getType() == RobotType.CARRIER) {
                int dist = distance(pos, ally.getLocation());
                if(dist > farthest) {
                    farthest = dist;
                    far = ally.getLocation();
                }
            }
        }

        if(far == null) {
            moveTowards(rc, headquarters);
            return;
        }

        moveTowards(rc, far);
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

        int radius = rc.getType().visionRadiusSquared;
        Team opponent = rc.getTeam().opponent();
        RobotInfo[] inRange = rc.senseNearbyRobots(radius, opponent);

        if (rc.isActionReady() && inRange.length > 0) {
            // MapLocation toAttack = enemies[0].location;
            targetPrio = launcherPriority.indexOf(inRange[0].getType());
            MapLocation target = inRange[0].location;
            for (RobotInfo enemy : inRange) {
                RobotType etype = enemy.getType();
                if (launcherPriority.indexOf(etype) < targetPrio) {
                    targetPrio = launcherPriority.indexOf(etype);
                    target = enemy.location;
                }
            }
            if (rc.canAttack(target)) {
                rc.attack(target);
                if (rc.isMovementReady() && (lstate == LauncherState.RUSHING)) {
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

    private static void writeToEmptyTarget(RobotController rc, int write, boolean hq) throws GameActionException {
        for(int i = 33; i < 44; i += 2)
            if(rc.readSharedArray(i) == 0) {
                rc.writeSharedArray(i, write);
                return;
            }

        //We know there was no open slot to write in. If hq, should override slot with no alpha assigned.
        if(hq) {
            for (int i = 32; i < 43; i += 2) {
                if (rc.readSharedArray(i) == 0) {
                    rc.writeSharedArray(i + 1, write);
                    return;
                }
            }
        }
    }
}
