package io.github.pauloafonsofraga.puzzlejogo;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import java.util.ArrayList;
import java.util.List;

public class Main extends ApplicationAdapter {
    public static final int TILE_SIZE = 80;  // Tamanho do pixel para cada elemento da grelha

    private SpriteBatch batch;      // Usado para renderizar as texturas
    private Texture wallTex;        // Textura para parede
    private Texture floorTex;       // Textura para chao
    private Texture crateTex;       // Textura para caixa
    private Texture robotTex;       // Textura para robot
    private Texture goalTex;        // Textura para objectivo
    private BitmapFont font;        // Fonte para a mensagem "Ganhaste!"

    private enum Cell { FLOOR, WALL }  // Representa se cada celula é chao ou parede
    private Cell[][] cells;            // layout das celulas
    private List<Vector2> cratePositions;  // lista das coordenadas das caixas
    private List<Vector2> goalPositions;   // lista das coordenadas dos objectivos
    private Vector2 robotPos;              // actual posicao do robot
    private int mapWidth, mapHeight;       // dimensao do mapa em celulas

    // Camera para renderizar a janela
    private OrthographicCamera camera;
    private Viewport viewport;

    private boolean facingRight = true;  // analisa se o robot se o robot esta virado para a direita

    @Override
    public void create() {
        // inicia o spritebatch para desenhar os sprites
        batch = new SpriteBatch();

        // vai buscar as textura à pasta assets
        wallTex  = new Texture("wall.png");
        floorTex = new Texture("floor.png");
        crateTex = new Texture("crate.png");
        robotTex = new Texture("robot.png");
        goalTex  = new Texture("goal.png");

        // cria uma nova fonte para o texto "Ganhaste!"
        font = new BitmapFont();
        // escala a fonte para consguirmos ler
        font.getData().setScale(2f);

        // initaliza as listas para as caixas e objectivos
        cratePositions = new ArrayList<>();
        goalPositions  = new ArrayList<>();

        // Constroi o nivel
        loadLevel();

        // Faz resize ao nivel para caber no ecra
        camera   = new OrthographicCamera();
        viewport = new FitViewport(mapWidth * TILE_SIZE, mapHeight * TILE_SIZE, camera);
        viewport.apply();

        // Posiciona a camera no centro do nivel
        camera.position.set(mapWidth * TILE_SIZE / 2f, mapHeight * TILE_SIZE / 2f, 0);
        camera.update();

        batch.setProjectionMatrix(camera.combined);
    }

    private void loadLevel() {
        // Layout em ASCII do primeiro nivel do jogo Sokoban
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

        // Numero de linhas no mapa
        mapHeight = raw.length;
        // Determina a largura maxima das linhas
        for (String row : raw) {
            mapWidth = Math.max(mapWidth, row.length());
        }
        // Faz o array das celulas tendo em conta as dimensoes calculadas
        cells = new Cell[mapHeight][mapWidth];

        // preenche as celulas, cratePositions, goalPositions, e robotPos
        for (int y = 0; y < mapHeight; y++) {
            // inverte y para a linha 0 estar no fundo do ecra
            String row = raw[mapHeight - 1 - y];
            for (int x = 0; x < mapWidth; x++) {
                char c = x < row.length() ? row.charAt(x) : ' ';
                if (c == 'X') {
                    // 'X' representa uma parede
                    cells[y][x] = Cell.WALL;
                } else {
                    // tudo o resto é chao
                    cells[y][x] = Cell.FLOOR;
                    if (c == '.')   goalPositions.add(new Vector2(x, y));  // '.' é objectivo
                    if (c == '*')   cratePositions.add(new Vector2(x, y)); // '*' posicao inicial das caixas
                    if (c == '@')   robotPos = new Vector2(x, y);          // '@' posicao inicial do robot
                }
            }
        }
    }

    @Override
    public void resize(int width, int height) {
        // Faz update ao viewport quando a janela muda de tamanho
        viewport.update(width, height);
    }

    @Override
    public void render() {
        // input do keyboard para movimento do robot
        handleInput();

        //  Limpa o ecra antes de comecar o render
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.begin();

        // Desehha o mapa: chao, paredes e objectivos
        for (int y = 0; y < mapHeight; y++) {
            for (int x = 0; x < mapWidth; x++) {

                Texture tex = (cells[y][x] == Cell.WALL) ? wallTex : floorTex;

                batch.draw(tex, x * TILE_SIZE, y * TILE_SIZE, TILE_SIZE, TILE_SIZE);
                // Se nesta posicao tem um objectivo faz um overlay
                if (goalPositions.contains(new Vector2(x, y))) {
                    batch.draw(goalTex, x * TILE_SIZE, y * TILE_SIZE, TILE_SIZE, TILE_SIZE);
                }
            }
        }

        // Desenha cada caixa na posicao certa
        for (Vector2 crate : cratePositions) {
            batch.draw(crateTex, crate.x * TILE_SIZE, crate.y * TILE_SIZE, TILE_SIZE, TILE_SIZE);
        }

        // Desenha o robot
        float rx = robotPos.x * TILE_SIZE;
        float ry = robotPos.y * TILE_SIZE;
        if (facingRight) {
            // Textura normal, nada muda
            batch.draw(robotTex, rx, ry, TILE_SIZE, TILE_SIZE);
        } else {
            // Vira a textura ao contrario
            batch.draw(robotTex, rx + TILE_SIZE, ry, -TILE_SIZE, TILE_SIZE);
        }

        // Se todas as caixas estao nos objectivos imprime a mensagem de vitoria
        if (isWin()) {
            font.draw(batch,
                "Ganhaste!",
                (mapWidth * TILE_SIZE) / 2f - 160,
                (mapHeight * TILE_SIZE) / 2f + 40);
        }

        batch.end();  // Batch para renderizar termina
    }

    private void handleInput() {
        // Movimento quando se carrega nas setas do teclado
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP))    tryMove(0,  1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN))  tryMove(0, -1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT))  tryMove(-1,  0);
        if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT)) tryMove(1,   0);
    }

    private void tryMove(int dx, int dy) {
        // actualiza a taxtura para movimento horizontal
        if (dx < 0) facingRight = false;  // a mover-se para a esquerda
        if (dx > 0) facingRight = true;   // a mover-se para a direita

        // Calcula a celula para onde se vai mover o robot
        Vector2 next = new Vector2(robotPos.x + dx, robotPos.y + dy);
        if (isBlocked(next)) return;  // o robot nao pode passar paredes

        // Verifica se a caixa ocupa uma celula de objectivo
        Vector2 hit = findCrateAt(next);
        if (hit != null) {
            // Tenta empurrar a caixa para a celula seguinte
            Vector2 after = new Vector2(hit.x + dx, hit.y + dy);
            // se a celula esta bloqueada ou ocupada por outra caixa, o movimento para
            if (isBlocked(after) || findCrateAt(after) != null) return;
            // senao a caixa move
            hit.set(after);
        }

        // actualiza a posicao do robot
        robotPos.set(next);
    }

    private boolean isBlocked(Vector2 pos) {
        int x = (int) pos.x, y = (int) pos.y;
        // bloqueia nas fronteiras do mapa
        if (x < 0 || x >= mapWidth || y < 0 || y >= mapHeight) return true;
        // bloqueia nas paredes
        return cells[y][x] == Cell.WALL;
    }

    private Vector2 findCrateAt(Vector2 pos) {

        for (Vector2 c : cratePositions) {
            if (c.epsilonEquals(pos, 0f)) return c;
        }
        return null;
    }

    private boolean isWin() {
        // verifica se todos os objectivos estao com uma caixa
        for (Vector2 g : goalPositions) {
            boolean covered = false;
            for (Vector2 c : cratePositions) {
                if (c.epsilonEquals(g, 0.1f)) { covered = true; break; }
            }
            if (!covered) return false;  // se algum dos objectivos nao tem caixa, nao deixa ganhar
        }
        return true;  // todos os objectivos estao cumpridos
    }



}
