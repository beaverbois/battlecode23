package USQualifiers;

import battlecode.common.*;
import scala.Int;

import java.util.*;

import static USQualifiers.CarrierSync.*;
import static USQualifiers.HQSync.*;
import static USQualifiers.Launcher.*;
import static USQualifiers.LauncherSync.*;
import static USQualifiers.RobotPlayer.*;
import static USQualifiers.Util.*;

public class Carrier {
    enum CarrierState {
        SCOUTING,
        MOVING,
        FARMING,
        RETURNING,
        ISLAND
    }

    static boolean reportingWell = false;
    private static Direction scoutDirection = null;
    static CarrierState state = null;
    static boolean stateLock = false;
    static int hqID = 0;
    static MapLocation hqLocation = null;
    static MapLocation targetWellLocation = null;
    static final int maxCollectionCycles = 15;
    static int numCycles = 0;
    static List<Direction> shuffledDir;
    public static ResourceType targetType = null;

    static boolean reportingEnemy = false;
    static boolean pathBlocked = false;
    static boolean islandCarrier = false;
    static Direction blockedTraverseDirection = null;
    static Direction blockedTargetDirection = null;
    static MapLocation enemyTarget = null;
    static ArrayList<MapLocation> wellsFarmed = new ArrayList<>();

    static HashMap<Integer, Integer> islands = new HashMap<>();

    static int lastCarried = turnCount;

    static void run(RobotController rc) throws GameActionException {
        if (state == null) {
                // this will run when the bot is created
                state = CarrierState.SCOUTING;
                hqID = getHQNum(rc);
                hqLocation = readHQLocation(rc, hqID);
                targetType = readCarrierAssignment(rc, hqID);
                scoutDirection = hqLocation.directionTo(rc.getLocation());

                shuffledDir = new ArrayList<>(Arrays.asList(directions));

                //Do islands if instructed to.
                if (readIsland(rc, hqID) == 1) {
                    islandCarrier = true;
                    state = CarrierState.ISLAND;
                    System.out.println("Islands are here");
                }
        }

        if(!islandCarrier) senseEnemies(rc);

        rc.setIndicatorString(state.toString());

        switch (state) {
            case SCOUTING:
                // if we have not discovered all wells, scout in a direction away from hq
                if (!stateLock) {
                    if (readNumWellsFound(rc, hqID) < 2) {
                        stateLock = true;
                        scout(rc);
                    } else {
                        // if we have discovered all wells, set our targetWell
                        targetWellLocation = readWellLocation(rc, targetType, hqID);
                        state = CarrierState.MOVING;
                        moveTowardsTargetWell(rc);
                        break;
                    }
                } else {
                    scout(rc);
                }

                break;

            case MOVING:
                moveTowardsTargetWell(rc);
                break;

            case FARMING:
                farm(rc);
                break;

            case RETURNING:
                returningToHQ(rc);
                break;
            case ISLAND:
                islands(rc);
                break;
        }
    }

    private static void scout(RobotController rc) throws GameActionException {
        rc.setIndicatorString(state.toString() + " " + targetType);
        if (!rc.isMovementReady() || isJammed(rc)) {
            return;
        }
        // once we have picked an initial direction, go in that direction till we can no longer
        MapLocation loc = rc.getLocation().add(scoutDirection);
        if (rc.onTheMap(loc) && rc.sensePassability(loc)) {
            if (rc.canMove(scoutDirection)) {
                rc.move(scoutDirection);
                scout(rc);
            }
        } else {
            // if we can't go that way, randomly pick another direction until one is found

            Collections.shuffle(shuffledDir);
            boolean changed = false;
            for (Direction dir : shuffledDir) {
                if (dir != scoutDirection.opposite() && rc.canMove(dir)) {
                    changed = true;
                    scoutDirection = dir;
                    rc.move(scoutDirection);
                    scout(rc);
                }
            }

            if (!changed) {
                if (rc.canMove(scoutDirection.opposite())) {
                    scoutDirection = scoutDirection.opposite();
                    rc.move(scoutDirection);
                    scout(rc);
                }
            }
        }

        // if all wells are discovered while scouting, set our target well and move towards it
        if (isWellDiscovered(rc, targetType, hqID)) {
            targetWellLocation = readWellLocation(rc, targetType, hqID);
            state = CarrierState.MOVING;
            moveTowardsTargetWell(rc);
        } else {
            // when we discover a nearby well, make sure it is the right type and not already stored before we write it
            WellInfo[] wells = rc.senseNearbyWells(targetType);
            if (wells.length > 0) {
                targetWellLocation = wells[0].getMapLocation();
                if (rc.canWriteSharedArray(0, 1)) {
                    writeWell(rc, targetType, targetWellLocation, hqID);
                } else {
                    reportingWell = true;
                    state = CarrierState.RETURNING;
                }
            }
        }

        // Record spotted islands.
        int[] islandID = rc.senseNearbyIslands();
        for(int island : islandID) {
            if(islands.get(island) != null) {
                islands.replace(island, islands.get(island) + (rc.senseTeamOccupyingIsland(island) == robotTeam ? 10000 : 0));
                continue;
            }
            MapLocation[] islandLocs = rc.senseNearbyIslandLocations(island);
            islands.put(island, locToInt(islandLocs[0]) + (rc.senseTeamOccupyingIsland(island) == robotTeam ? 10000 : 0));
        }
    }

    private static void moveTowardsTargetWell(RobotController rc) throws GameActionException {
        // check we are not on a current
        if (rc.senseMapInfo(rc.getLocation()).getCurrentDirection() != Direction.CENTER) {
            return;
        }

        // check if we are already adjacent to a well or if we cannot move
        if  (canFarm(rc) || !rc.isMovementReady()) {
            return;
        }

        // move towards the closest square available around target well
        MapLocation targetLocation = closestAvailableLocationTowardsRobot(rc, targetWellLocation);
        Direction targetDir;
        if (targetLocation != null) {
            targetDir = closestAvailableDirectionAroundRobot(rc, targetLocation);
        } else {
            targetDir = closestAvailableDirectionAroundRobot(rc, targetWellLocation);
        }

        if (targetDir != null) {
            rc.move(targetDir);
            rc.setIndicatorString("MOVING " + targetDir + " TO " + targetLocation);
        } else {
            //TODO: blocked is not being used correctly — should be wall traversal
            if (checkIfBlocked(rc, targetWellLocation)) {
                return;
            }
        }
        // check we are not on a current
        if (rc.senseMapInfo(rc.getLocation()).getCurrentDirection() != Direction.CENTER) {
            return;
        }

        // check if we are adjacent to a well and change state accordingly
        if (canFarm(rc)) {
            return;
        }

        // move a second time if we can
        if (rc.isMovementReady()) {
            // move towards the closest square available around target well
            targetLocation = null;
            targetDir = null;
            targetLocation = closestAvailableLocationTowardsRobot(rc, targetWellLocation);
            if (targetLocation != null) {
                targetDir = closestAvailableDirectionAroundRobot(rc, targetLocation);
            } else {
                targetDir = closestAvailableDirectionAroundRobot(rc, targetWellLocation);
            }

            if (targetDir != null) {
                rc.move(targetDir);
                rc.setIndicatorString("MOVING " + targetDir + " TO " + targetLocation);
            } else {
                if (checkIfBlocked(rc, targetWellLocation)) {
                    return;
                }
            }
        }
    }

    private static void farm(RobotController rc) throws GameActionException {
        // if we can collect resources, do so, if not move back towards well
        if (!checkAndCollectResources(rc)) {
            state = CarrierState.MOVING;
            moveTowardsTargetWell(rc);
            return;
        }

        // once we reach maxCollectionCycles, return and move towards hq
        if (numCycles >= maxCollectionCycles) {
            state = CarrierState.RETURNING;
            wellsFarmed.add(targetWellLocation);
            numCycles = 0;

            moveTowards(rc, hqLocation);
            rc.setIndicatorString(state.toString() + " TO " + hqLocation);
        }
    }

    private static void returningToHQ(RobotController rc) throws GameActionException {
        if (reportingEnemy && rc.canWriteSharedArray(0, 0)) {
            reportEnemy(rc, enemyTarget, false);
            reportingEnemy = false;
            state = CarrierState.SCOUTING;
            return;
        }

        if (reportingWell) {
            rc.setIndicatorString("Reporting Well");
            if (isWellDiscovered(rc, targetType, hqID)) {
                targetWellLocation = readWellLocation(rc, targetType, hqID);
                reportingWell = false;

                state = CarrierState.MOVING;
                moveTowardsTargetWell(rc);
                return;
            } else if (rc.canWriteSharedArray(0, 1)) {
                writeWell(rc, targetType, targetWellLocation, hqID);
                reportingWell = false;

                state = CarrierState.MOVING;
                moveTowardsTargetWell(rc);
                return;
            }
        }

        // if we are already at hq, transfer and set state to moving
        if (checkHQAdjacencyAndTransfer(rc)) {
            return;
        }

        rc.setIndicatorString(state.toString() + " TO " + hqLocation);
        if (wellsFarmed.size() > 0) checkForAuxWellsAndMove(rc);

        if (!moveTowards(rc, hqLocation)) {
            if (checkIfBlocked(rc, hqLocation)) {
                return;
            }
        }

        checkHQAdjacencyAndTransfer(rc);
    }

    private static void islands(RobotController rc) throws GameActionException {
        rc.setIndicatorString("ISLANDS");

        readIslands(rc);

        //Record spotted islands.
        int[] islandID = rc.senseNearbyIslands();
        for(int island : islandID) {
            if(islands.containsKey(island)) {
                islands.replace(island, islands.get(island) % 10000 + (rc.senseTeamOccupyingIsland(island) == rc.getTeam() ? 10000 : 0));
                continue;
            }
            MapLocation[] islandLocs = rc.senseNearbyIslandLocations(island);
            islands.put(island, locToInt(islandLocs[0]) + (rc.senseTeamOccupyingIsland(island) == rc.getTeam() ? 10000 : 0));
        }

        MapLocation pos = rc.getLocation();

        //If we don't have an anchor, try to pick one up. If we can't, return to HQ and vibe.
        if(rc.getAnchor() == null) {
            if(rc.canTakeAnchor(hqLocation, Anchor.STANDARD)) {
                rc.takeAnchor(hqLocation, Anchor.STANDARD);
            }
            else {
                if(!pos.isAdjacentTo(hqLocation)) moveTowards(rc, hqLocation);
                if(rc.canWriteSharedArray(0, 0)) writeIslands(rc);
                if(turnCount - lastCarried > 50) perish(rc);
                return;
            }
        }

        //Determine the closest unclaimed island
        int id = 0;
        double close = 120;
        MapLocation target = null;
        for (Map.Entry<Integer, Integer> entry : islands.entrySet()) {
            MapLocation loc = intToLoc(entry.getValue() % 10000);
            if(entry.getValue() / 10000 == 0 && dist(pos, loc) < close) {
                id = entry.getKey();
                target = loc;
                close = dist(pos, target);
            }
        }

        if(target == null) {
            if(corner == null) {
                int cornerX = rng.nextInt(2) == 1 ? rc.getMapWidth() - 4 : 4;
                int cornerY = rng.nextInt(2) == 1 ? rc.getMapHeight() - 4 : 4;

                corner = new MapLocation(cornerX, cornerY);
            }

            if(rc.canSenseLocation(corner)) {
                //No islands here, choose another corner.
                System.out.println("Corner Pre: " + corner);
                int cornerX = corner.x;
                int cornerY = corner.y;

                if(cornerX > rc.getMapWidth() / 2 ^ cornerY > rc.getMapWidth() / 2) cornerX = rc.getMapWidth() - cornerX;
                else cornerY = rc.getMapHeight() - cornerY;

                corner = new MapLocation(cornerX, cornerY);
                System.out.println("Corner Post: " + corner);
            }

            moveTowards(rc, corner);
            return;
        }

        //Right now, I'm having carriers just die if they meet resistance. It seems unlikely they'd be able to escape a launcher,
        //so I'm just having them ignore enemies and go for a suicide rush if that happens.
        MapLocation[] islandLocs = rc.senseNearbyIslandLocations(id);
        if(islandLocs.length == 0) {
            moveTowards(rc, target);
            return;
        }

        MapLocation closeIsland = closest(pos, islandLocs);
        if(moveTowards(rc, closeIsland) && rc.canMove(pos.directionTo(closeIsland))) rc.move(pos.directionTo(closeIsland));

        pos = rc.getLocation();

        if(rc.senseIsland(pos) == id && rc.canPlaceAnchor()) {
            //Place the anchor
            rc.placeAnchor();
            islands.replace(id, locToInt(target) + 10000);
            lastCarried = turnCount;
        }
    }

    private static void fighting(RobotController rc) throws GameActionException {
        if(rc.getResourceAmount(ResourceType.ADAMANTIUM) == 0) {
            moveTowards(rc, hqLocation);
            if(rc.canTransferResource(hqLocation, ResourceType.ADAMANTIUM, -1))
                rc.transferResource(hqLocation, ResourceType.ADAMANTIUM, -1);
            else if(rc.getLocation().isAdjacentTo(hqLocation)) state = CarrierState.SCOUTING;
            return;
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, opponentTeam);

        if(enemies.length == 0) {
            if (!rc.canSenseLocation(hqLocation)) {
                state = CarrierState.SCOUTING;
                return;
            }
            if (rc.canMove(scoutDirection)) {
                rc.move(scoutDirection);
                if (rc.canMove(scoutDirection)) {
                    rc.move(scoutDirection);
                }
            } else {
                // if we can't go that way, randomly pick another direction until one is found
                Collections.shuffle(shuffledDir);
                for (Direction dir : shuffledDir) {
                    if (rc.canMove(dir)) {
                        scoutDirection = dir;
                        rc.move(scoutDirection);
                        break;
                    }
                }
            }

            return;
        }

        MapLocation[] locs = new MapLocation[enemies.length];
        for (int i = 0; i < enemies.length; i++) {
            RobotInfo enemy = enemies[i];
            locs[i] = enemy.getLocation();
            if(rc.canAttack(locs[i])) {
                rc.attack(locs[i]);
                return;
            }
        }

        moveTowards(rc, closest(rc.getLocation(), locs));
    }

    private static void senseEnemies(RobotController rc) throws GameActionException {
        // If a headquarters is detected, report it back to HQ
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, opponentTeam);
        for (RobotInfo enemy : enemies) {
            RobotType enemyType = enemy.getType();
            if (enemyType == RobotType.LAUNCHER || enemyType == RobotType.DESTABILIZER) {
                //If a fighting enemy is detected, report it back to HQ and try to attack it
                enemyTarget = enemy.getLocation();
                if (rc.canAttack(enemyTarget)) {
                    rc.attack(enemyTarget);
                }
                state = CarrierState.RETURNING;
                reportingEnemy = !checkEnemy(rc, enemyTarget);
                if (reportingEnemy) return;
            }
        }
    }

    private static boolean checkIfBlocked(RobotController rc, MapLocation target) throws GameActionException {
        rc.setIndicatorString("Blocked!");
        MapLocation rcLocation = rc.getLocation();
        Direction targetDir = (pathBlocked) ? blockedTargetDirection: rcLocation.directionTo(target);
        MapLocation front = rcLocation.add(targetDir);

        boolean senseable = rc.canSenseLocation(front);

        //Consider currents that point towards you and adjacent tiles to be impassable.
        Direction current = senseable ? rc.senseMapInfo(front).getCurrentDirection() : null;

        boolean passable = senseable && rc.sensePassability(front) && (current == Direction.CENTER || dist(rcLocation, front.add(current)) > 1);

        if (senseable && !passable && !rc.canSenseRobotAtLocation(front)) {
            Direction[] wallFollow = {
                    targetDir.rotateRight().rotateRight(),
                    targetDir.rotateLeft().rotateLeft()};

            // Move in the same direction as we previously were when blocked
            if (pathBlocked) {
                if (rc.canMove(blockedTraverseDirection)) {
                    rc.move(blockedTraverseDirection);
                    return true;
                } else {
                    blockedTraverseDirection = blockedTraverseDirection.opposite();
                    if (rc.canMove(blockedTraverseDirection)) {
                        rc.move(blockedTraverseDirection);
                        return true;
                    }
                }
            } else {
                // Call moveTowards again to see if we are near well/still stuck
                for (Direction wallDir : wallFollow) {
                    if (rc.canMove(wallDir)) {
                        pathBlocked = true;
                        blockedTargetDirection = rcLocation.directionTo(target);
                        blockedTraverseDirection = wallDir;

                        rc.move(wallDir);
                        return true;
                    }
                }
            }
        } else {
            pathBlocked = false;
        }
        return false;
    }

    private static boolean checkHQAdjacencyAndTransfer(RobotController rc) throws GameActionException{
        if (rc.getLocation().isAdjacentTo(hqLocation)) {
            wellsFarmed.clear();

            if (rc.canTransferResource(hqLocation, ResourceType.ADAMANTIUM, 1)) {
                rc.transferResource(hqLocation, ResourceType.ADAMANTIUM, rc.getResourceAmount(ResourceType.ADAMANTIUM));
            }

            if (rc.canTransferResource(hqLocation, ResourceType.MANA, 1)) {
                rc.transferResource(hqLocation, ResourceType.MANA, rc.getResourceAmount(ResourceType.MANA));
            }

            if (!reportingWell && rc.getResourceAmount(ResourceType.ADAMANTIUM) == 0 && rc.getResourceAmount(ResourceType.MANA) == 0) {
                if (targetWellLocation == null) {
                    state = CarrierState.SCOUTING;
                    scout(rc);
                } else {
                    state = CarrierState.MOVING;

                    targetWellLocation = readWellLocation(rc, targetType, hqID);
                    moveTowardsTargetWell(rc);
                }
            }

            return true;
        }
        return false;
    }

    private static boolean canFarm(RobotController rc) throws GameActionException {
        if (rc.canCollectResource(targetWellLocation, -1)) {
            state = CarrierState.FARMING;
            numCycles = 0;
            farm(rc);

            return true;
        }
        return false;
    }

    private static boolean checkAndCollectResources(RobotController rc) throws GameActionException {
        if (rc.canCollectResource(targetWellLocation, -1)) {
            rc.collectResource(targetWellLocation, -1);
            numCycles++;
            rc.setIndicatorString(state.toString() + " CYCLE " + numCycles + "/" + maxCollectionCycles);
            return true;
        }
        return false;
    }

    private static void checkForAuxWellsAndMove(RobotController rc) throws GameActionException {
        WellInfo[] wells = rc.senseNearbyWells();

        for (WellInfo well : wells) {
            MapLocation wellLocation = well.getMapLocation();
            if (wellLocation != targetWellLocation && !wellsFarmed.contains(wellLocation)) {
                targetWellLocation = wellLocation;
                state = CarrierState.MOVING;
                moveTowardsTargetWell(rc);
                return;
            }
        }
    }

    // Determines which HQ spawned us by finding our ID amongst a list of IDs spawned
    private static int getHQNum(RobotController rc) throws GameActionException {
        int[] IDs = readCarrierSpawnIDs(rc);
        int rcID = rc.getID();

        for (int i = 0; i < IDs.length; i++) {
            if (IDs[i] == rcID) {
                return i;
            }
        }

        throw new GameActionException(GameActionExceptionType.OUT_OF_RANGE, "Could not find HQ ID!");
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
}
