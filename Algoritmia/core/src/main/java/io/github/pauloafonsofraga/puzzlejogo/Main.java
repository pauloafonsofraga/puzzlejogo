package io.github.pauloafonsofraga.puzzlejogo;

import com.badlogic.gdx.ApplicationAdapter;  // Base class for LibGDX applications
import com.badlogic.gdx.Gdx;                 // Provides access to the application and its environment
import com.badlogic.gdx.Input;               // Defines input constants (e.g., keys)
import com.badlogic.gdx.graphics.GL20;       // Contains OpenGL constants for clearing buffers
import com.badlogic.gdx.graphics.OrthographicCamera;  // Camera for 2D rendering
import com.badlogic.gdx.graphics.Texture;    // Represents an image loaded into GPU memory
import com.badlogic.gdx.graphics.g2d.BitmapFont;  // For drawing text
import com.badlogic.gdx.graphics.g2d.SpriteBatch; // Batch for efficient sprite rendering
import com.badlogic.gdx.math.Vector2;        // 2D vector class
import com.badlogic.gdx.utils.viewport.FitViewport; // Viewport that maintains aspect ratio
import com.badlogic.gdx.utils.viewport.Viewport;    // Base viewport class

import java.util.ArrayList;  // Array-backed List implementation
import java.util.List;       // Interface for ordered collections

public class Main extends ApplicationAdapter {
    public static final int TILE_SIZE = 80;  // Pixel size of each tile

    private SpriteBatch batch;      // Used to draw textures efficiently
    private Texture wallTex;        // Texture for wall tiles
    private Texture floorTex;       // Texture for floor tiles
    private Texture crateTex;       // Texture for crates
    private Texture robotTex;       // Texture for robot/player
    private Texture goalTex;        // Texture for goal markers
    private BitmapFont font;        // Font for drawing "You win!"

    private enum Cell { FLOOR, WALL }  // Represents whether a cell is floor or wall
    private Cell[][] cells;            // 2D array of cells (map layout)
    private List<Vector2> cratePositions;  // List of crate coordinates
    private List<Vector2> goalPositions;   // List of goal coordinates
    private Vector2 robotPos;              // Current robot/player position
    private int mapWidth, mapHeight;       // Dimensions of the map in tiles

    // Camera and viewport for rendering the world within the window
    private OrthographicCamera camera;
    private Viewport viewport;

    private boolean facingRight = true;  // Tracks which way the robot is facing

    @Override
    public void create() {
        // Instantiate the SpriteBatch for drawing sprites
        batch = new SpriteBatch();

        // Load all textures from the assets folder
        wallTex  = new Texture("wall.png");
        floorTex = new Texture("floor.png");
        crateTex = new Texture("crate.png");
        robotTex = new Texture("robot.png");
        goalTex  = new Texture("goal.png");

        // Create a default BitmapFont for text rendering
        font = new BitmapFont();
        // Scale up the font so that the "You win!" text is readable
        font.getData().setScale(2f);

        // Initialize lists for crates and goals
        cratePositions = new ArrayList<>();
        goalPositions  = new ArrayList<>();

        // Build the level data (cells, crates, goals, robot start)
        loadLevel();

        // Set up camera centered on the world, using a FitViewport to handle resizing
        camera   = new OrthographicCamera();
        viewport = new FitViewport(mapWidth * TILE_SIZE, mapHeight * TILE_SIZE, camera);
        viewport.apply();  // Apply the viewport configuration

        // Position camera at the center of the map in world coordinates
        camera.position.set(mapWidth * TILE_SIZE / 2f, mapHeight * TILE_SIZE / 2f, 0);
        camera.update();   // Update camera matrices

        // Tell the batch to use the camera's combined projection & view matrices
        batch.setProjectionMatrix(camera.combined);
    }

    private void loadLevel() {
        // Raw ASCII layout for the original DOS Sokoban level 1 (26 cols by 11 rows)
        String[] raw = {
            "        XXXXX           ",
            "        X   X           ",
            "        X*  X           ",
            "      XXX  *XXX         ",
            "      X  *  * X         ",
            "    XXX X XXX X     XXXXXX",
            "    X   X XXX XXXXXXX  ..X",
            "    X *  *             ..X",
            "    XXXXX XXXX X@XXXX  ..X",
            "        X      XXX  XXXXXX",
            "        XXXXXXXX        "
        };

        // Number of rows in the map
        mapHeight = raw.length;
        // Determine the maximum width among all rows
        for (String row : raw) {
            mapWidth = Math.max(mapWidth, row.length());
        }
        // Allocate the cells array using the computed dimensions
        cells = new Cell[mapHeight][mapWidth];

        // Populate the cells, cratePositions, goalPositions, and robotPos
        for (int y = 0; y < mapHeight; y++) {
            // Invert y so that row 0 is bottom of the screen
            String row = raw[mapHeight - 1 - y];
            for (int x = 0; x < mapWidth; x++) {
                // Get the character or blank if beyond string length
                char c = x < row.length() ? row.charAt(x) : ' ';
                if (c == 'X') {
                    // 'X' denotes a wall
                    cells[y][x] = Cell.WALL;
                } else {
                    // Everything else is floor
                    cells[y][x] = Cell.FLOOR;
                    if (c == '.')   goalPositions.add(new Vector2(x, y));  // '.' is goal
                    if (c == '*')   cratePositions.add(new Vector2(x, y)); // '*' is crate start
                    if (c == '@')   robotPos = new Vector2(x, y);          // '@' is player start
                }
            }
        }
    }

    @Override
    public void resize(int width, int height) {
        // When the window is resized, update the viewport to maintain aspect
        viewport.update(width, height);
    }

    @Override
    public void render() {
        // Handle keyboard input for movement & orientation
        handleInput();

        // Clear the screen to black before drawing
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.begin();

        // Draw the map: floors, walls, and goals
        for (int y = 0; y < mapHeight; y++) {
            for (int x = 0; x < mapWidth; x++) {
                // Choose wall or floor texture
                Texture tex = (cells[y][x] == Cell.WALL) ? wallTex : floorTex;
                // Draw the tile at the correct world position
                batch.draw(tex, x * TILE_SIZE, y * TILE_SIZE, TILE_SIZE, TILE_SIZE);
                // If there's a goal here, overlay it
                if (goalPositions.contains(new Vector2(x, y))) {
                    batch.draw(goalTex, x * TILE_SIZE, y * TILE_SIZE, TILE_SIZE, TILE_SIZE);
                }
            }
        }

        // Draw each crate at its current position
        for (Vector2 crate : cratePositions) {
            batch.draw(crateTex, crate.x * TILE_SIZE, crate.y * TILE_SIZE, TILE_SIZE, TILE_SIZE);
        }

        // Draw the robot/player, flipping if facing left
        float rx = robotPos.x * TILE_SIZE;
        float ry = robotPos.y * TILE_SIZE;
        if (facingRight) {
            // Normal draw: facing right
            batch.draw(robotTex, rx, ry, TILE_SIZE, TILE_SIZE);
        } else {
            // Flipped draw: offset x by TILE_SIZE and draw negative width
            batch.draw(robotTex, rx + TILE_SIZE, ry, -TILE_SIZE, TILE_SIZE);
        }

        // If all crates are on goals, display a win message
        if (isWin()) {
            font.draw(batch,
                "You win!",
                (mapWidth * TILE_SIZE) / 2f - 80,
                (mapHeight * TILE_SIZE) / 2f + 20);
        }

        batch.end();  // Finish the drawing batch
    }

    private void handleInput() {
        // On arrow key presses, attempt to move and update facing
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP))    tryMove(0,  1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN))  tryMove(0, -1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT))  tryMove(-1,  0);
        if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT)) tryMove(1,   0);
    }

    private void tryMove(int dx, int dy) {
        // Update facing direction based on horizontal movement
        if (dx < 0) facingRight = false;  // moving left
        if (dx > 0) facingRight = true;   // moving right

        // Calculate target cell for robot
        Vector2 next = new Vector2(robotPos.x + dx, robotPos.y + dy);
        if (isBlocked(next)) return;  // can't move into walls or out of bounds

        // Check if a crate occupies the target cell
        Vector2 hit = findCrateAt(next);
        if (hit != null) {
            // Attempt to push the crate one more cell in same direction
            Vector2 after = new Vector2(hit.x + dx, hit.y + dy);
            // If that cell is blocked or occupied by another crate, abort
            if (isBlocked(after) || findCrateAt(after) != null) return;
            // Otherwise move the crate
            hit.set(after);
        }

        // Finally update the robot position
        robotPos.set(next);
    }

    private boolean isBlocked(Vector2 pos) {
        int x = (int) pos.x, y = (int) pos.y;
        // Out of map bounds is blocked
        if (x < 0 || x >= mapWidth || y < 0 || y >= mapHeight) return true;
        // Walls are blocked
        return cells[y][x] == Cell.WALL;
    }

    private Vector2 findCrateAt(Vector2 pos) {
        // Check each crate for a match at the given position
        for (Vector2 c : cratePositions) {
            if (c.epsilonEquals(pos, 0f)) return c;
        }
        return null;  // no crate found
    }

    private boolean isWin() {
        // Verify every goal position has a crate on it
        for (Vector2 g : goalPositions) {
            boolean covered = false;
            for (Vector2 c : cratePositions) {
                if (c.epsilonEquals(g, 0.1f)) { covered = true; break; }
            }
            if (!covered) return false;  // if any goal is empty, not a win
        }
        return true;  // all goals covered
    }

    @Override
    public void dispose() {
        // Clean up resources when the application exits
        batch.dispose();
        wallTex.dispose();
        floorTex.dispose();
        crateTex.dispose();
        robotTex.dispose();
        goalTex.dispose();
        font.dispose();
    }
}
