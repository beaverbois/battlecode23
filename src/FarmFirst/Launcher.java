package FarmFirst;

import battlecode.common.*;

import static FarmFirst.RobotPlayer.directions;
import static FarmFirst.RobotPlayer.rng;

public class Launcher {
    static enum LauncherState {
        RUSHING,
        DEFENDING,
        REPORTING
    }

    static void run(RobotController rc) throws GameActionException {
        // Try to attack someone
        Team opponent = rc.getTeam().opponent();
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, opponent);
        for (RobotInfo enemy : enemies) {
            if (enemy.getType() != RobotType.HEADQUARTERS) {
                MapLocation toAttack = enemy.location;

                if (rc.canAttack(toAttack)) {
                    rc.setIndicatorString("Attacking");
                    rc.attack(toAttack);
                }

                Direction moveDir = rc.getLocation().directionTo(enemy.getLocation());
                if (rc.canMove(moveDir)) {
                    rc.move(moveDir);
                }
                break;
            }
        }

        Direction dir = directions[rng.nextInt(directions.length)];
        if (rc.canMove(dir)) {
            rc.move(dir);
        }
    }
}
