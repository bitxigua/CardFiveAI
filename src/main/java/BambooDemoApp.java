import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * 展示 1-9 条子的示例应用。
 */
public class BambooDemoApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);
        grid.setPadding(new Insets(20));

        for (int i = 1; i <= 9; i++) {
            BambooTile tile = new BambooTile(i);
            StackPane wrapper = new StackPane(tile);
            wrapper.setStyle("-fx-background-color: #eaeaea; -fx-padding: 8; -fx-background-radius: 8;");
            int col = (i - 1) % 3;
            int row = (i - 1) / 3;
            grid.add(wrapper, col, row);
        }

        Scene scene = new Scene(grid);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Mahjong Bamboo Tiles Demo");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
