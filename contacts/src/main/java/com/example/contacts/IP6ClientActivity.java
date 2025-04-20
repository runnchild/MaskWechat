package com.example.contacts;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.Socket;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class IP6ClientActivity extends AppCompatActivity {
    private static final int PORT = 6000;
    private ServerSocket serverSocket;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.connectButton).setOnClickListener(v -> {
            // 启动 TCP 服务器线程
            new Thread(this::startServer).start();
        });
    }

    private void startServer() {
        try {
            serverSocket = new ServerSocket(PORT);
            Log.d("Server", "Server started on port " + PORT);

            while (!serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept();
                Log.d("Server", "Client connected: " + clientSocket.getInetAddress());

                // 处理客户端请求
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            Log.e("Server", "Error: " + e.getMessage());
        }
    }

    private void handleClient(Socket clientSocket) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

            // 读取客户端请求
            String request = in.readLine();
            Log.d("Server", "Received: " + request);

            // 解析请求并处理业务逻辑
            String response = processRequest(request);

            // 返回响应
            out.println(response);

            // 关闭连接
            clientSocket.close();
        } catch (IOException e) {
            Log.e("Server", "Client error: " + e.getMessage());
        }
    }

    private String processRequest(String request) {
        // 业务逻辑处理（示例：返回大写字符串）
        return request.toUpperCase();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {
            Log.e("Server", "Failed to close server: " + e.getMessage());
        }
    }
}