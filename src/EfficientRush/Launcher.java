package EfficientRush;

import battlecode.common.*;

import java.util.Map;

import static EfficientRush.RobotPlayer.*;
import static Utilities.Util.*;

public class Launcher {

    static enum LauncherState {
        RUSHING,
        REPORTING,
        SWARMING,
        SUPPRESSING
    }
    static LauncherState lstate = LauncherState.RUSHING;

    static RobotInfo[] enemies;

    static MapLocation pos;

    static MapLocation targetOppHQ;
    static int oppHQStatus = 0;

    static void run(RobotController rc) throws GameActionException {
        rc.setIndicatorString(lstate.toString());

        switch(lstate) {
            case RUSHING: rushing(rc); break;
            case REPORTING: reporting(rc); break;
            case SWARMING: swarming(rc); break;
            case SUPPRESSING: suppressing(rc); break;
        }
    }

    private static void turnStart(RobotController rc) throws GameActionException {
        pos = rc.getLocation();

        for (int i = 0; i < allHQ.length; i++) {
            int read = rc.readSharedArray(i + 4) % 10000;
            if (read != 0 && read != locToInt(allOpposingHQ[i])) {
                if (locToInt(allOpposingHQ[i]) == 0) {
                    allOpposingHQ[i] = intToLoc(read);
                }
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

        attack(rc);

        int radius = rc.getType().actionRadiusSquared;
        Team ally = rc.getTeam();
        RobotInfo[] allies = rc.senseNearbyRobots(radius, ally);

        if (rc.isMovementReady()) {
            //Trying running to the center
            MapLocation center = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
            if(distance(pos, center) < 3 && allies.length > 4) moveTowards(rc, new MapLocation(rc.getMapWidth() - headquarters.x, rc.getMapHeight() - headquarters.y));
            else moveTowards(rc, center);
        }

        scout(rc);
        if(lstate == LauncherState.REPORTING) return;

        int count = 0;
        MapLocation[] untakenHQ = new MapLocation[4];
        for (int i = 0; i < allOpposingHQ.length; i++) {
            if (locToInt(allOpposingHQ[i]) == 0) break;
            if (rc.readSharedArray(i + 4) / 10000 == 0) untakenHQ[count++] = allOpposingHQ[i];
        }
        if(count != 0) {
            MapLocation[] knownOppHQ = new MapLocation[count];
            System.arraycopy(untakenHQ, 0, knownOppHQ, 0, count);
            targetOppHQ = closest(pos, knownOppHQ);
            lstate = LauncherState.SWARMING;
        }

        attack(rc);
    }

    private static void reporting(RobotController rc) throws GameActionException {
        turnStart(rc);

        attack(rc);

        if (rc.canWriteSharedArray(0, 0)) {
            //Update shared array with enemy HQ and status updates on suppressed HQ.
            for(int i = 0; i < allHQ.length; i++) {
                int read = rc.readSharedArray(4+i);
                int oppHQ = locToInt(allOpposingHQ[i]);
                if (oppHQ != 0 && (read == 0 || (targetOppHQ != null && targetOppHQ.equals(allOpposingHQ[i])))) {
                    if(targetOppHQ != null && targetOppHQ.equals(allOpposingHQ[i])) rc.writeSharedArray(4 + i, 10000 * oppHQStatus + oppHQ);
                    else rc.writeSharedArray(4 + i, oppHQ);
                }
            }
            lstate = LauncherState.RUSHING;
        } else if (rc.isMovementReady()) {
            //Move towards closest headquarters.
            headquarters = closest(pos, allHQ);
            moveTowards(rc, headquarters);
        }

        attack(rc);
    }

    private static void swarming(RobotController rc) throws GameActionException {
        turnStart(rc);

        attack(rc);

        Team ally = rc.getTeam();
        RobotInfo[] allies = rc.senseNearbyRobots(targetOppHQ, 9, ally);

        int numAttackers = allies.length;
        int numDefenders = enemies.length;

        for(RobotInfo bot : allies) if(bot.getType() != RobotType.LAUNCHER) numAttackers--;

        for(RobotInfo bot : enemies) if(bot.getType() != RobotType.LAUNCHER && bot.getType() != RobotType.DESTABILIZER) numDefenders--;

        int suppressiveForce = 3;

        int difference = numAttackers - numDefenders;

        if (rc.isMovementReady()) {
            moveTowards(rc, targetOppHQ);
        }
        if(distance(pos, targetOppHQ) < 3) lstate = LauncherState.SUPPRESSING;
        if(distance(pos, targetOppHQ) < 3 && difference > suppressiveForce) {
            oppHQStatus = 1;
            lstate = LauncherState.REPORTING;
        }

        attack(rc);
    }

    private static void suppressing(RobotController rc) throws GameActionException {
        //Tentatively intentionally allowing HQ to spawn bots to waste resources.
        turnStart(rc);

        attack(rc);

        Team ally = rc.getTeam();
        RobotInfo[] allies = rc.senseNearbyRobots(targetOppHQ, 4, ally);

        if(distance(pos, targetOppHQ) > 2) moveTowards(rc, targetOppHQ);

        int numAttackers = allies.length;

        for(RobotInfo bot : allies) if(bot.getType() != RobotType.LAUNCHER) numAttackers--;

        if(distance(pos, targetOppHQ) > 2 && numAttackers > 5) lstate = LauncherState.RUSHING;

        attack(rc);
    }

    private static void scout(RobotController rc) throws GameActionException {
        for (RobotInfo enemy : enemies) {
            if (enemy.getType() == RobotType.HEADQUARTERS) {
                int loc = locToInt(enemy.getLocation());
                int numHQ = allHQ.length;
                for (int i = 0; i < numHQ; i++) {
                    int val = locToInt(allOpposingHQ[i]);
                    if (val == loc) break;
                    if (val == 0) {
                        allOpposingHQ[i] = enemy.getLocation();
                        lstate = LauncherState.REPORTING;
                        break;
                    }
                }
            }
        }
    }

    private static void attack(RobotController rc) throws GameActionException {
        int targetPrio;
        RobotInfo[] inRange = rc.senseNearbyRobots(rc.getType().actionRadiusSquared, rc.getTeam().opponent());
        if (rc.isActionReady() && inRange.length > 0) {
            // MapLocation toAttack = enemies[0].location;
            targetPrio = launcherPriority.indexOf(inRange[0].getType());
            int targetHealth = inRange[0].getHealth();
            MapLocation target = inRange[0].location;
            int dist = distance(pos, target);
            boolean switchTarget = false;
            for (RobotInfo enemy : inRange) {
                RobotType etype = enemy.getType();
                int eprio = launcherPriority.indexOf(etype);
                if (eprio <= targetPrio) {
                    if(eprio < targetPrio) switchTarget = true;
                    else if(enemy.getHealth() <= targetHealth) {
                        if(distance(pos, target) <= dist || enemy.getHealth() < targetHealth) switchTarget = true;
                    }
                }
                if(switchTarget) {
                    targetHealth = enemy.getHealth();
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

    private static void moveRandom(RobotController rc) throws GameActionException {
        int randDir = rng.nextInt(directions.length);
        Direction dir = directions[randDir++ % directions.length];
        for (int i = 0; i < directions.length && !rc.canMove(dir); i++) {
            dir = directions[randDir++ % directions.length];
        }
        if (rc.canMove(dir)) rc.move(dir);
    }
}
