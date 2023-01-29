package USQualifiers;

import battlecode.common.*;

import java.util.*;

import static USQualifiers.LauncherSync.*;
import static USQualifiers.RobotPlayer.*;
import static USQualifiers.Util.*;

public class Launcher {

    static enum LauncherState {
        GATHERING,
        REPORTING,
        SWARMING,
        SUPPRESSING,
        PACK
    }
    static LauncherState lstate = LauncherState.GATHERING;

    static final ArrayList<RobotType> launcherPriority = new ArrayList<RobotType>(Arrays.asList(
            RobotType.DESTABILIZER,
            RobotType.LAUNCHER,
            RobotType.BOOSTER,
            RobotType.CARRIER,
            RobotType.AMPLIFIER,
            RobotType.HEADQUARTERS
    ));

    static RobotInfo[] nearbyRobots, enemies, allies;

    static RobotInfo[] packStatus;

    static MapLocation pos;

    static MapLocation target;
    static int oppHQStatus = 0;

    static int numMoves = 0;

    static Direction wallDir = null;
    static boolean offWall = false;

    static boolean withinOppHQRange = false;
    static boolean targetReported = false;

    static final int MIN_PACK_SIZE = 5, RETREAT = 3;

    static boolean attacked = false;

    static HashMap<MapLocation, Integer> bfsMap = new HashMap<>();
    static HashMap<MapLocation, MapLocation[]> adjList;

    //Wells
    static boolean reportingWell = false, reportingEnemy = false, reportingHQ = false, reportingSuspect = false;
    static MapLocation targetWellLocation = null;
    static ResourceType targetWellType = null;

    static final int TOWARDS_CENTER_MOD = 2;

    static MapLocation[] suspectedOppHQ;
    static int suspectCount = 0;
    static boolean stateLock = false;
    static MapLocation[] allHQ;
    static MapLocation[] allOpposingHQ;
    static MapLocation headquarters = new MapLocation(0, 0);
    static MapLocation corner = null;
    static MapLocation newKnownHQ;

    static Direction pastWall;
    static boolean stuck = false;

    static ArrayList<MapLocation> lastPos = new ArrayList<>();
    static int lastPosSize = 5;

    static boolean pathBlocked = false;
    static Direction blockedTraverseDirection = null;
    static Direction blockedTargetDirection = null;

    static void run(RobotController rc) throws GameActionException {
        if (!stateLock) {
            setup(rc);
            stateLock = true;
        }

        rc.setIndicatorString(lstate.toString());

        attacked = false;

        switch(lstate) {
            case GATHERING: gathering(rc); break;
            case REPORTING: reporting(rc); break;
            case SWARMING: swarming(rc); break;
            case SUPPRESSING: suppressing(rc); break;
            case PACK: pack(rc); break;
        }

        lastPos.add(pos);
        if(lastPos.size() > lastPosSize) lastPos.remove(0);
    }

    private static void turnStart(RobotController rc) throws GameActionException {
        pos = rc.getLocation();

        readOppHeadquarters(rc);

        updateSuspected(rc);
        int temp = suspectCount;
        while(suspectedOppHQ[suspectCount].equals(new MapLocation(120, 120))) {
            suspectCount = (suspectCount + 1) % suspectedOppHQ.length;
            if(suspectCount == temp) break;
        }

        //If reporting and suspectCount changes, someone else has reported.
        if(reportingSuspect && temp != suspectCount) lstate = LauncherState.GATHERING;

        nearbyRobots = rc.senseNearbyRobots();
        List<RobotInfo> alliesL = new ArrayList<>();
        List<RobotInfo> enemiesL = new ArrayList<>();

        for (RobotInfo bot : nearbyRobots) {
            if (bot.team == robotTeam && bot.type == RobotType.LAUNCHER) alliesL.add(bot);
            else if (bot.team == opponentTeam) enemiesL.add(bot);
        }

        allies = new RobotInfo[alliesL.size()];
        enemies = new RobotInfo[enemiesL.size()];

        allies = alliesL.toArray(allies);
        enemies = enemiesL.toArray(enemies);

        if (packStatus == null) packStatus = allies;

        scout(rc);
    }

    private static void gathering(RobotController rc) throws GameActionException {
        turnStart(rc);

        attack(rc);

        if (lstate == LauncherState.REPORTING) return;

        if (rc.isMovementReady()) {
            //If an enemy launcher is seen, move some units towards it.
            //Trying moving towards the center of all HQ, then moving out as a pack.
            int centerX = 0, centerY = 0;
            for (MapLocation mapLocation : allHQ) {
                centerX += mapLocation.x;
                centerY += mapLocation.y;
            }
            MapLocation hqCenter = new MapLocation(centerX / allHQ.length, centerY / allHQ.length);

            int towardsCenterX, towardsCenterY;

            MapLocation gatherPoint;

            //Gather

            towardsCenterX = (rc.getMapWidth() / 2 - hqCenter.x) / TOWARDS_CENTER_MOD;
            towardsCenterY = (rc.getMapHeight() / 2 - hqCenter.y) / TOWARDS_CENTER_MOD;

            gatherPoint = new MapLocation(hqCenter.x + towardsCenterX, hqCenter.y + towardsCenterY);

            rc.setIndicatorString("GATHERING, " + gatherPoint);

           if (distance(pos, gatherPoint) > 1) moveTowards(rc, gatherPoint);
        }

        MapLocation[] t = closestTargetHQ(rc);

        if(t != null && (allies.length > MIN_PACK_SIZE || withinOppHQRange)) {
            lstate = LauncherState.SWARMING;
            target = t[0];
        }
        else if(allies.length > MIN_PACK_SIZE) {
            lstate = LauncherState.PACK;
            chooseTarget(rc);
        }

        attack(rc);

        packStatus = allies;
    }

    private static void reporting(RobotController rc) throws GameActionException {
        turnStart(rc);
        if(lstate == LauncherState.GATHERING) return;

        attack(rc);

        if (rc.canWriteSharedArray(0, 0)) {
            if (reportingHQ) {
                reportHQ(rc);
                reportingHQ = false;
            }

            if(reportingSuspect) {
                writeSuspected(rc, false);
                reportingSuspect = false;
            }

            if(reportingEnemy) {
                reportEnemy(rc, target, targetReported);
                reportingEnemy = false;
            }

            if (reportingWell) {
                // TODO: Eventually ugprade to new well system
//                reportWell(rc, targetWellLocation, targetWellType);
                reportingWell = false;
            }

            lstate = LauncherState.GATHERING;
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

        if(allies.length < RETREAT && !withinOppHQRange && !rc.senseCloud(pos)) {
            lstate = LauncherState.GATHERING;
            return;
        }

        int suppressiveForce = 4;

        moveTowards(rc, target);

        if(rc.canSenseLocation(target)) {
            RobotInfo[] suppressors = rc.senseNearbyRobots(target, 16, robotTeam);

            if (distance(pos, target) < 3) lstate = LauncherState.SUPPRESSING;
            if (distance(pos, target) < 4 && suppressors.length - enemies.length > suppressiveForce) {
                MapLocation[] close = closestTargetHQ(rc);
                if (close != null && close[0] == target) {
                    oppHQStatus = 1;
                    lstate = LauncherState.REPORTING;
                    reportingHQ = true;
                } else if (close != null) target = close[0];
            }
        }

        attack(rc);

        packStatus = allies;
    }

    private static void suppressing(RobotController rc) throws GameActionException {
        turnStart(rc);

        attack(rc);

        if (rc.isMovementReady() && withinSquaredRadius(pos, target, 9)) moveAway(rc, target);
        else if (rc.isMovementReady() && !withinSquaredRadius(pos, target, 16)) moveTowards(rc, target);
//
//
//        Team ally = rc.getTeam();
//        RobotInfo[] allies = rc.senseNearbyRobots(target, 4, ally);
//
//        int numAttackers = allies.length;
//
//        for (RobotInfo bot : allies) if (bot.getType() != RobotType.LAUNCHER) numAttackers--;
//
//        if(distance(pos, target) > 1 && numAttackers > 3) lstate = LauncherState.GATHERING;

        attack(rc);
    }

    private static void pack(RobotController rc) throws GameActionException {
        turnStart(rc);
        if(lstate == LauncherState.REPORTING) return;

        chooseTarget(rc);

        rc.setIndicatorString("Pack " + target);

        attack(rc);

        int avrX = pos.x, avrY = pos.y;

        //TODO: Implement moving towards injured allies (aka health decreased since last turn).
        for (RobotInfo ally : allies) {
            avrX += ally.getLocation().x;
            avrY += ally.getLocation().y;
        }

        MapLocation avrPos = new MapLocation(avrX / (allies.length + 1), avrY / (allies.length + 1));

        if(enemies.length == 0 && distance(pos, target) < 4 && distance(pos, target) > distance(avrPos, target) - 1) {
            lstate = LauncherState.REPORTING;
            if(targetReported) reportingEnemy = true;
            else reportingSuspect = true;
            return;
        } else if(enemies.length == 0 && distance(pos, target) < 4) chooseTarget(rc);

        if(allies.length < RETREAT && !rc.senseCloud(pos)) {
            lstate = LauncherState.GATHERING;
            return;
        }

        moveTowards(rc, target);

        if(distance(pos, target) < 3 && withinOppHQRange) lstate = LauncherState.SUPPRESSING;

        //If within vision distance of target and there are no enemies, swap target.
        packStatus = allies;
        attack(rc);
    }

    private static void scout(RobotController rc) throws GameActionException {
        //TODO: Uncomment
//        lookForWells(rc);

        for (RobotInfo enemy : enemies) {
            if (enemy.getType() == RobotType.HEADQUARTERS) {
                withinOppHQRange = true;
                int loc = locToInt(enemy.getLocation());
                int numHQ = allHQ.length;
                for (int i = 0; i < numHQ; i++) {
                    int val = locToInt(allOpposingHQ[i]);
                    if (val == loc) {
                        break;
                    }
                    if (val == 0) {
                        allOpposingHQ[i] = enemy.getLocation();
                        lstate = LauncherState.REPORTING;
                        newKnownHQ = enemy.getLocation();
                        reportingHQ = true;
                        System.out.println("Spotted HQ " + enemy.getLocation());
                        break;
                    }
                }
                return;
            }
        }

        withinOppHQRange = false;
    }

    // TODO: Migrate to new well system
//    private static void lookForWells(RobotController rc) throws GameActionException {
//        //Well scouting
//        // when we discover a nearby well, make sure it is the right type and not already stored before we write it
//
//        boolean foundAll = true;
//
//        //Make sure we haven't found every well yet.
//        for (int i = WELL_INDEX_MIN; i < WELL_INDEX_MAX; i++) {
//            if (rc.readSharedArray(i) == 0) {
//                foundAll = false;
//                break;
//            }
//        }
//
//        if (foundAll) return;
//
//        WellInfo[] wells = rc.senseNearbyWells();
//
//        if (wells.length > 0) {
//            // make a location list of all stored wells of each type
//            ArrayList<MapLocation> adWellLocations = new ArrayList<>();
//            ArrayList<MapLocation> mnWellLocations = new ArrayList<>();
//            for (int i = WELL_INDEX_MIN; i <= WELL_INDEX_MAX; i++) {
//                int read = rc.readSharedArray(i);
//                if (ResourceType.values()[read / 10000] == ResourceType.ADAMANTIUM) adWellLocations.add(intToLoc(read % 10000));
//                else if (ResourceType.values()[read / 10000] == ResourceType.MANA) mnWellLocations.add(intToLoc(read % 10000));
//            }
//
//            // we only want to store numWellsStored/2 wells per type, not elixir yet
//            if (adWellLocations.size() < NUM_WELLS_STORED / 2 || mnWellLocations.size() < NUM_WELLS_STORED / 2) {
//                // check if any wells we found are new and not stored
//                for (WellInfo well : wells) {
//                    MapLocation loc = well.getMapLocation();
//                    ResourceType type = well.getResourceType();
//                    if ((type == ResourceType.MANA && mnWellLocations.size() < NUM_WELLS_STORED / 2 && !mnWellLocations.contains(loc))
//                            || (type == ResourceType.ADAMANTIUM && adWellLocations.size() < NUM_WELLS_STORED / 2 && !adWellLocations.contains(loc))) {
//                        targetWellLocation = loc;
//                        targetWellType = type;
//                        // otherwise, return to hq to report
//                        reportingWell = true;
//                        lstate = LauncherState.REPORTING;
//                        return;
//                    }
//                }
//            }
//        }
//    }

    private static void attack(RobotController rc) throws GameActionException {
        if(attacked) return;
        int targetPrio;
        if (rc.isActionReady() && enemies.length > 0) {
            targetPrio = launcherPriority.indexOf(enemies[0].getType());
            int targetHealth = enemies[0].getHealth();
            MapLocation targetLoc = enemies[0].location;
            for (RobotInfo enemy : enemies) {
                MapLocation loc = enemy.location;
                if (!enemy.getType().equals(RobotType.HEADQUARTERS) && rc.canActLocation(loc)) {
                    RobotType etype = enemy.getType();
                    int eprio = launcherPriority.indexOf(etype);
                    if (eprio < targetPrio || (eprio <= targetPrio && enemy.getHealth() < targetHealth)) {
                        targetHealth = enemy.getHealth();
                        targetPrio = launcherPriority.indexOf(etype);
                        targetLoc = loc;
                    }
                }
            }
            if (rc.canSenseRobotAtLocation(targetLoc) && !rc.senseRobotAtLocation(targetLoc).type.equals(RobotType.HEADQUARTERS) && rc.canAttack(targetLoc)) {
                rc.attack(targetLoc);
                attacked = true;
                if (rc.isMovementReady() && rc.canSenseRobotAtLocation(targetLoc)) {
                    RobotInfo robot = rc.senseRobotAtLocation(targetLoc);
                    if(robot.type == RobotType.LAUNCHER) {
                        Direction away = targetLoc.directionTo(pos);
                        Direction[] directionsAway = {
                                away,
                                away.rotateRight(),
                                away.rotateLeft(),
                                away.rotateRight().rotateRight(),
                                away.rotateLeft().rotateLeft()
                        };
                        for(Direction dir : directionsAway) {
                            if(rc.canMove(dir)) {
                                rc.move(dir);
                                pathBlocked = false;
                                return;
                            }
                        }
                    }
                    else if(robot.type == RobotType.CARRIER && lstate != LauncherState.REPORTING) moveTowards(rc, targetLoc);
                }
            }
        }

        //Randomly attack a cloud if we don't expect to move this turn.
        if(!rc.isMovementReady() && rc.isActionReady()) {
            MapLocation[] clouds = rc.senseNearbyCloudLocations();
            if(clouds.length != 0) {
                int randCloud = rng.nextInt(clouds.length);
                if (rc.canAttack(clouds[randCloud])) rc.attack(clouds[randCloud]);
            }
        }
    }

    private static void bugNavTowards(RobotController rc, MapLocation location) throws GameActionException {
        pos = rc.getLocation();
        if(!rc.isMovementReady()) return;
        Direction p = genPath(rc, location);
        if(p != null) {
            pathBlocked = false;
            Direction[] dirTowards = {
                    p,
                    p.rotateRight(),
                    p.rotateLeft()
            };

            for(Direction dir : dirTowards) {
                if(rc.canMove(dir)) {
                    rc.move(dir);
                    return;
                }
            }
            return;
        }
        if(!pathBlocked) {
            offWall = false;
            wallDir = null;

            boolean passable = false;
            Direction towards = pos.directionTo(location);

            Direction[] dirTowards = {
                    towards,
                    towards.rotateRight(),
                    towards.rotateLeft()
            };

            for(Direction dir : dirTowards) {
                if(rc.sensePassability(pos.add(dir))) passable = true;
                if(rc.canMove(dir)) {
                    rc.move(dir);
                    return;
                }
            }

            if(!passable) {
                pathBlocked = true;
            }
        } else {
            if(wallDir == null) wallDir = pos.directionTo(location);
            MapLocation wall = pos.add(wallDir);

            //If we can sense the target and move towards it, do so.
            if(rc.canSenseLocation(location) && rc.canMove(pos.directionTo(location))) {
                rc.move(pos.directionTo(location));
                pathBlocked = false;
                wallDir = null;
                return;
            }

            if(offWall) {
                if(rc.canMove(wallDir)) {
                    rc.move(wallDir);
                    if(!rc.onTheMap(rc.getLocation().add(wallDir)) || !rc.sensePassability(rc.getLocation().add(wallDir))) offWall = false;
                    return;
                }
                if(rc.canMove(wallDir.rotateLeft())) {
                    rc.move(wallDir.rotateLeft());
                    if(!rc.onTheMap(rc.getLocation().add(wallDir)) || !rc.sensePassability(rc.getLocation().add(wallDir))) offWall = false;
                    return;
                }
            }

            if(rc.onTheMap(wall) && rc.sensePassability(wall)) {
                //We're past the edge of the wall.
                if(rc.canMove(wallDir)) {
                    rc.move(wallDir);
                    if(!offWall) wallDir = wallDir.rotateRight().rotateRight();
                    return;
                }
                //Should only get here if there's a robot blocking the path. If so, check adjacent directions.
                Direction[] dirTowards = {
                        wallDir.rotateRight(),
                        wallDir.rotateLeft()
                };
                for(Direction dir : dirTowards) {
                    if(rc.canMove(dir)) {
                        rc.move(dir);
                        if(rc.sensePassability(pos.add(dir))) offWall = true;
                        return;
                    }
                }

                //If our better paths are blocked, just chill for the moment.
                return;
            }

            Direction moveDir = wallDir.rotateLeft().rotateLeft();

            if(rc.onTheMap(pos.add(moveDir)) && rc.sensePassability(pos.add(moveDir))) {
                //Continue moving along the wall
                if(rc.canMove(moveDir)) {
                    rc.move(moveDir);
                    return;
                }

                //Move along a diagonal
                Direction[] dirTowards = {
                        moveDir.rotateRight(),
                        moveDir.rotateLeft()
                };
                for(Direction dir : dirTowards) {
                    if(rc.canMove(dir)) {
                        rc.move(dir);
                        if(rc.sensePassability(pos.add(dir))) offWall = true;
                        return;
                    }
                }

                return;
            }

            //Found a corner.
            if(!rc.onTheMap(pos.add(moveDir)) || !rc.sensePassability(pos.add(moveDir))) {
                //First, check if we can sneak through a diagonal.
                if(rc.onTheMap(pos.add(moveDir.rotateRight())) && rc.sensePassability(pos.add(moveDir.rotateRight()))) {
                    if(rc.canMove(moveDir.rotateRight())) {
                        rc.move(moveDir.rotateRight());
                        wallDir = wallDir.rotateRight().rotateRight();
                        return;
                    }

                    //Blocked by a robot, chill for now.
                    return;
                }

                //Otherwise, just continue along the new wall if possible.
                if(!rc.onTheMap(pos.add(wallDir.opposite())) || !rc.sensePassability(pos.add(wallDir.opposite()))) {
                    wallDir = moveDir;
                    if(rc.canMove(moveDir.opposite())) {
                        rc.move(moveDir);
                    }
                    return;
                }

                //If not possible, we must be trapped in a corner. Bug nav around the other side of the wall.
                wallDir = wallDir.opposite();
                bugNavTowards(rc, location);
                return;
            }

            //Should never get here.
            System.out.println("Somehow got here, just gonna go boom.");
            perish(rc);
        }
    }

    private static Direction genPath(RobotController rc, MapLocation target) throws GameActionException {
        MapLocation pos = rc.getLocation();
        Direction towards = rc.getLocation().directionTo(target);
        if(rc.getLocation().isAdjacentTo(target)) return towards;
        if(rc.canSenseLocation(target)) {
            if(!rc.sensePassability(target)) return null;
            ArrayList<Direction> p = new ArrayList<>();
            int numSteps = 10;
            p = pathTo(rc, pos, target, p, numSteps);
            if(p != null) return p.get(0);
        }
        return null;
    }

    private static ArrayList<Direction> pathTo(RobotController rc, MapLocation loc, MapLocation target, ArrayList<Direction> steps, int maxSteps) throws GameActionException {
        System.out.println("Bytecode: " + Clock.getBytecodeNum());
        if(loc.equals(target)) return steps;
        if(steps.size() > maxSteps) return null;
        Direction dir = loc.directionTo(target);
        Direction[] dirTowards = {
                dir,
                dir.rotateRight(),
                dir.rotateLeft(),
                dir.rotateRight().rotateRight(),
                dir.rotateLeft().rotateLeft(),
        };
        for(Direction d : dirTowards) {
            if((steps.size() == 0 || steps.get(steps.size() - 1).opposite() != d) && canPassThrough(rc, loc, loc.add(d))) {
                steps.add(d);
                ArrayList<Direction> p = pathTo(rc, loc.add(d), target, steps, maxSteps);
                if(p != null) return p;
            }
        }
        return null;
    }

    private static boolean canPassThrough(RobotController rc, MapLocation loc, MapLocation target) throws GameActionException {
        if(!rc.onTheMap(target) || !rc.canSenseLocation(target)) return false;
        if(loc.isAdjacentTo(target) && rc.sensePassability(target)
                && rc.senseMapInfo(target).getCurrentDirection() != target.directionTo(loc)) return true;
        return false;
    }

    private static void moveTowards(RobotController rc, MapLocation location) throws GameActionException {
        // check if we cannot move
        if (!rc.isMovementReady()) {
            return;
        }

        rc.setIndicatorString("Moving towards " + location);

        if (checkIfBlocked(rc, location)) {
            return;
        }

        MapLocation targetLocation = closestAvailableLocationTowardsRobot(rc, location, true);
        Direction targetDir;
        if (targetLocation != null) {
            targetDir = closestAvailableDirectionAroundRobot(rc, targetLocation);
        } else {
            targetDir = closestAvailableDirectionAroundRobot(rc, location);
        }

        if (targetDir != null) {
            rc.move(targetDir);
        }
    }

    private static boolean checkIfBlocked(RobotController rc, MapLocation target) throws GameActionException {

        pos = rc.getLocation();
        Direction targetDir = (pathBlocked) ? blockedTargetDirection : pos.directionTo(target);
        MapLocation front = pos.add(targetDir);

        boolean senseable = rc.canSenseLocation(front);

        //Consider currents that point towards you and adjacent tiles to be impassable.
        Direction current = senseable ? rc.senseMapInfo(front).getCurrentDirection() : null;

        boolean passable = senseable && rc.sensePassability(front) && (current == Direction.CENTER || dist(pos, front.add(current)) > 1);

        if (senseable && !passable && !rc.canSenseRobotAtLocation(front)) {
            //rc.setIndicatorString("Blocked!");
            Direction[] wallFollow = {
                    targetDir.rotateRight(),
                    targetDir.rotateLeft(),
                    targetDir.rotateRight().rotateRight(),
                    targetDir.rotateLeft().rotateLeft()};

            // Move in the same direction as we previously were when blocked
            if (pathBlocked) {
                if(rc.canMove(pos.directionTo(target)) && !pos.directionTo(target).equals(blockedTraverseDirection.opposite())) {
                    rc.move(pos.directionTo(target));
                    return true;
                } else if (rc.canMove(blockedTraverseDirection)) {
                    rc.move(blockedTraverseDirection);
                    return true;
                }
//                else {
//                    blockedTraverseDirection = blockedTraverseDirection.opposite();
//                    if (rc.canMove(blockedTraverseDirection)) {
//                        rc.move(blockedTraverseDirection);
//                        return true;
//                    }
//                }
            }
            for (Direction wallDir : wallFollow) {
                if (rc.canMove(wallDir)) {
                    //blockedTargetDirection = pos.directionTo(target);
                    blockedTraverseDirection = wallDir;
                    blockedTargetDirection = blockedTraverseDirection;
                    pathBlocked = true;

                    rc.move(wallDir);
                    return true;
                }
            }
        } else {
            pathBlocked = false;
        }

        return false;
    }

//    private static void moveTowards(RobotController rc, MapLocation location) throws GameActionException {
//        boolean moved = false;
//
//        // check if we cannot move
//        if (!rc.isMovementReady()) {
//            return;
//        }
//
//        pos = rc.getLocation();
//
//        numMoves = 0;
//
//        if (checkIfBlocked(rc, location)) {
//            return;
//        }
//
//        for (Direction dir : closestDirections(rc, location, pos)) {
//            MapLocation closestSquare = location.add(dir);
//            Direction closestSquareDir = pos.directionTo(closestSquare);
//
//            // ensure we do not move towards a wall/impassible square
//            if (rc.canSenseLocation(closestSquare) && !rc.sensePassability(closestSquare)) {
//                continue;
//            }
//
//            if (rc.canMove(closestSquareDir)) {
//                rc.move(closestSquareDir);
//                moved = true;
//                rc.setIndicatorString(lstate.toString() + " TO " + closestSquare + " DESTINATION " + location);
//                numMoves++;
//                break;
//            }
//        }
//
//        if (checkIfBlocked(rc, location)) {
//            return;
//        }
//
//        // robot has not moved, so move to a random square around us closest to well
//        if (numMoves == 0) {
//            for (Direction dir : closestDirections(rc, pos, location)) {
//                if (rc.canMove(dir)) {
//                    rc.move(dir);
//                    moved = true;
//                    rc.setIndicatorString(lstate.toString() + " TO " + pos.add(dir) + " DESTINATION " + location);
//                    break;
//                }
//            }
//        }
//
//        // move a second time if we can
//        if (rc.isMovementReady() && moved) {
//            moveTowards(rc, location);
//        }
//    }
//
//    private static boolean checkIfBlocked(RobotController rc, MapLocation target) throws GameActionException {
//        pos = rc.getLocation();
//        Direction targetDir = (pathBlocked) ? blockedTargetDirection: pos.directionTo(target);
//        MapLocation front = pos.add(targetDir);
//
//        boolean senseable = rc.canSenseLocation(front);
//
//        //Consider currents that point towards you and adjacent tiles to be impassable.
//        Direction current = senseable ? rc.senseMapInfo(front).getCurrentDirection() : null;
//
//        boolean passable = senseable && rc.sensePassability(front) && (current == Direction.CENTER || dist(pos, front.add(current)) > 1);
//
//        if (senseable && !passable && !rc.canSenseRobotAtLocation(front)) {
//            rc.setIndicatorString("Blocked!");
//            Direction[] wallFollow = {
//                    targetDir.rotateRight().rotateRight(),
//                    targetDir.rotateLeft().rotateLeft()};
//
//            // Move in the same direction as we previously were when blocked
//            if (pathBlocked) {
//                if (rc.canMove(blockedTraverseDirection)) {
//                    rc.move(blockedTraverseDirection);
//                    numMoves++;
//                    return true;
//                } else {
//                    blockedTraverseDirection = blockedTraverseDirection.opposite();
//                    if (rc.canMove(blockedTraverseDirection)) {
//                        rc.move(blockedTraverseDirection);
//                        numMoves++;
//                        return true;
//                    }
//                }
//            } else {
//                // Call moveTowards again to see if we are near well/still stuck
//                for (Direction wallDir : wallFollow) {
//                    if (rc.canMove(wallDir)) {
//                        pathBlocked = true;
//                        blockedTargetDirection = pos.directionTo(target);
//                        blockedTraverseDirection = wallDir;
//
//                        rc.move(wallDir);
//                        numMoves++;
//                        return true;
//                    }
//                }
//            }
//        } else {
//            pathBlocked = false;
//        }
//        return false;
//    }
}