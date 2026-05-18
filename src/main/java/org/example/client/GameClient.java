package org.example.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.example.ui.GameApp;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class GameClient extends Application {
    private TextField hostField;
    private TextField portField;
    private Label statusLabel;
    private Button connectButton;

    public static void main(String[] args) {
        System.setProperty("prism.allowhidpi", "true");
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        Label title = new Label("连接服务器");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        hostField = new TextField("127.0.0.1");
        hostField.setPromptText("请输入服务器 IP");

        portField = new TextField("5000");
        portField.setPromptText("请输入端口");

        statusLabel = new Label("输入服务器地址后点击连接");
        statusLabel.setStyle("-fx-text-fill: #6b4f2a;");

        connectButton = new Button("连接");
        connectButton.setDefaultButton(true);
        connectButton.setOnAction(event -> connect(stage));

        VBox root = new VBox(12,
                title,
                new Label("服务器 IP"),
                hostField,
                new Label("端口"),
                portField,
                statusLabel,
                connectButton
        );
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #f8edd8, #ead2a5);");

        stage.setTitle("连接揭棋服务器");
        stage.setScene(new Scene(root, 360, 280));
        stage.show();
    }

    private void connect(Stage connectStage) {
        String host = hostField.getText().trim();
        String portText = portField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            showError("端口必须是数字");
            return;
        }

        connectButton.setDisable(true);
        hostField.setDisable(true);
        portField.setDisable(true);
        statusLabel.setText("正在连接服务器...");

        Thread thread = new Thread(() -> {
            try {
                Socket socket = new Socket(host, port);
                Platform.runLater(() -> statusLabel.setText("已连接服务器，等待另一位玩家加入..."));

                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                String colorMessage = String.valueOf(in.readObject());
                String seedMessage = String.valueOf(in.readObject());
                String turnMessage = String.valueOf(in.readObject());
                String color = colorMessage.contains("RED") ? "RED" : "BLACK";
                long seed = Long.parseLong(seedMessage.substring(seedMessage.indexOf(':') + 1));
                boolean initialTurn = Boolean.parseBoolean(turnMessage.substring(turnMessage.indexOf(':') + 1));

                Platform.runLater(() -> {
                    try {
                        statusLabel.setText("匹配成功，正在进入棋盘...");
                        GameApp.configure(socket, in, out, color, seed, initialTurn);
                        Stage gameStage = new Stage();
                        new GameApp().start(gameStage);
                        connectStage.close();
                    } catch (Exception e) {
                        restoreInputState();
                        showError("打开棋盘失败: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    restoreInputState();
                    showError("连接失败: " + e.getMessage());
                });
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private void restoreInputState() {
        connectButton.setDisable(false);
        hostField.setDisable(false);
        portField.setDisable(false);
        statusLabel.setText("输入服务器地址后点击连接");
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
