package org.example.client;

import com.google.gson.JsonObject;
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
import org.example.common.GameBoard;
import org.example.protocol.JsonProtocol;
import org.example.ui.GameApp;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

public class GameClient extends Application {
    private TextField hostField;
    private TextField portField;
    private Label statusLabel;
    private Button connectButton;
    private WebSocketClient client;

    public static void main(String[] args) {
        System.setProperty("prism.allowhidpi", "true");
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        String initialHost = getParameters().getRaw().size() > 0 ? getParameters().getRaw().get(0) : "127.0.0.1";
        String initialPort = getParameters().getRaw().size() > 1 ? getParameters().getRaw().get(1) : "5000";
        Label title = new Label("连接服务器");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        hostField = new TextField(initialHost);
        hostField.setPromptText("请输入服务器 IP");

        portField = new TextField(initialPort);
        portField.setPromptText("请输入端口");

        statusLabel = new Label("输入服务器地址后点击连接");
        statusLabel.setStyle("-fx-text-fill: #6b4f2a;");

        connectButton = new Button("连接并匹配");
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

        try {
            client = new WebSocketClient(new URI("ws://" + host + ":" + port)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    Platform.runLater(() -> statusLabel.setText("已连接，正在匹配对手..."));
                    send(JsonProtocol.message("startMatch").toString());
                }

                @Override
                public void onMessage(String message) {
                    handleMessage(connectStage, message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Platform.runLater(() -> {
                        if (!GameApp.isRunning()) {
                            restoreInputState();
                            statusLabel.setText("连接已关闭: " + reason);
                        }
                    });
                }

                @Override
                public void onError(Exception ex) {
                    Platform.runLater(() -> {
                        restoreInputState();
                        showError("连接失败: " + ex.getMessage());
                    });
                }
            };
            client.connect();
        } catch (Exception e) {
            restoreInputState();
            showError("连接失败: " + e.getMessage());
        }
    }

    private void handleMessage(Stage connectStage, String message) {
        JsonObject json;
        try {
            json = JsonProtocol.parse(message);
        } catch (Exception e) {
            Platform.runLater(() -> showError("服务器返回了无效 JSON: " + e.getMessage()));
            return;
        }
        String messageType = JsonProtocol.typeOf(json);
        if ("matchSuccess".equals(messageType)) {
            Platform.runLater(() -> statusLabel.setText("匹配成功，等待开局..."));
            return;
        }
        if ("gameStart".equals(messageType)) {
            Platform.runLater(() -> openGame(connectStage, json));
            return;
        }
        GameApp.receive(json);
    }

    private void openGame(Stage connectStage, JsonObject json) {
        try {
            String color = JsonProtocol.string(json, "yourColor", "red");
            boolean firstHand = JsonProtocol.bool(json, "firstHand", false);
            GameBoard board = JsonProtocol.boardFromInitial(json.getAsJsonArray("initialBoard"));
            GameApp.configure(client, color, board, firstHand);
            Stage gameStage = new Stage();
            new GameApp().start(gameStage);
            connectStage.close();
        } catch (Exception e) {
            restoreInputState();
            showError("打开棋盘失败: " + e.getMessage());
        }
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
