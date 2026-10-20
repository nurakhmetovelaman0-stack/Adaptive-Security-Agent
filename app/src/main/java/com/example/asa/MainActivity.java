
package com.example.asa;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private LinearLayout root;
    private TextView statusView;
    private TextView resultView;
    private TextView logView;

    private volatile boolean stopped = false;
    private volatile boolean checking = false;

    private final int dark = Color.rgb(20, 25, 35);
    private final int green = Color.rgb(32, 150, 95);
    private final int blue = Color.rgb(40, 105, 190);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildInterface();
        logEvent("APP_STARTED", "ASA launched");
        showReadyState();
    }

    private void buildInterface() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 36, 28, 28);
        root.setBackgroundColor(dark);

        TextView title = new TextView(this);
        title.setText("ASA");
        title.setTextSize(36);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, 1);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("ADAPTIVE SECURITY AGENT");
        subtitle.setTextSize(12);
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 0, 0, 28);
        root.addView(subtitle, matchWrap());

        statusView = new TextView(this);
        statusView.setTextColor(Color.WHITE);
        statusView.setTextSize(18);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(16, 22, 16, 22);
        statusView.setBackgroundColor(Color.rgb(45, 55, 70));
        root.addView(statusView, matchWrap());

        TextView section = new TextView(this);
        section.setText("Диагностика");
        section.setTextColor(Color.WHITE);
        section.setTextSize(20);
        section.setTypeface(null, 1);
        section.setPadding(0, 28, 0, 12);
        root.addView(section, matchWrap());

        resultView = new TextView(this);
        resultView.setTextColor(Color.rgb(220, 225, 235));
        resultView.setTextSize(14);
        resultView.setPadding(16, 16, 16, 16);
        resultView.setBackgroundColor(Color.rgb(30, 37, 49));
        root.addView(resultView, matchWrap());

        Button scanButton = new Button(this);
        scanButton.setText("Запустить проверку");
        scanButton.setOnClickListener(v -> runDiagnostics());
        root.addView(scanButton, matchWrap());

        Button stopButton = new Button(this);
        stopButton.setText("Аварийная остановка");
        stopButton.setOnClickListener(v -> emergencyStop());
        root.addView(stopButton, matchWrap());

        Button logsButton = new Button(this);
        logsButton.setText("Показать журнал");
        logsButton.setOnClickListener(v -> showLogs());
        root.addView(logsButton, matchWrap());

        logView = new TextView(this);
        logView.setTextColor(Color.LTGRAY);
        logView.setTextSize(12);
        logView.setPadding(0, 18, 0, 10);
        root.addView(logView, matchWrap());

        TextView footer = new TextView(this);
        footer.setText(
            "ASA 0.1.0 • Локальная диагностика\n" +
            "Не является системным антивирусом."
        );
        footer.setTextColor(Color.GRAY);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, 30, 0, 10);
        root.addView(footer, matchWrap());

        scroll.addView(root);
        setContentView(scroll);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private void showReadyState() {
        statusView.setText("● ОЖИДАНИЕ ПРОВЕРКИ");
        statusView.setBackgroundColor(Color.rgb(45, 55, 70));
        resultView.setText(
            "Проверки ещё не запускались.\n\n" +
            "Нажми «Запустить проверку»."
        );
        logView.setText("");
    }

    private void runDiagnostics() {
        if (checking) {
            return;
        }

        stopped = false;
        checking = true;

        statusView.setText("ПРОВЕРКА...");
        statusView.setBackgroundColor(blue);
        resultView.setText("Выполняется диагностика...");
        logEvent("DIAGNOSTICS_STARTED", "Manual scan");

        new Thread(() -> {
            StringBuilder report = new StringBuilder();
            int passed = 0;
            int warnings = 0;

            try {
                if (stopped) {
                    return;
                }

                report.append("ASA SELF-DIAGNOSTICS\n\n");

                // 1. Android environment
                report.append("Android: ")
                    .append(Build.VERSION.RELEASE)
                    .append(" (API ")
                    .append(Build.VERSION.SDK_INT)
                    .append(")\n");

                report.append("Package: ")
                    .append(getPackageName())
                    .append("\n\n");

                // 2. Private application directory
                File dataDir = getFilesDir();
                boolean dataOk = dataDir.exists()
                    && dataDir.isDirectory()
                    && dataDir.canWrite();

                report.append("[")
                    .append(dataOk ? "PASS" : "FAIL")
                    .append("] Private app directory\n");

                if (dataOk) {
                    passed++;
                } else {
                    warnings++;
                }

                logEvent("DATA_DIRECTORY_CHECK",
                    dataOk ? "PASS" : "FAIL");

                if (stopped) {
                    return;
                }

                // 3. Own APK presence
                String apkPath = getApplicationInfo().sourceDir;
                File apk = new File(apkPath);

                boolean apkOk = apk.exists()
                    && apk.isFile()
                    && apk.length() > 0;

                report.append("[")
                    .append(apkOk ? "PASS" : "FAIL")
                    .append("] Installed APK available\n");

                if (apkOk) {
                    passed++;
                } else {
                    warnings++;
                }

                logEvent("APK_FILE_CHECK",
                    apkOk ? "PASS" : "FAIL");

                // 4. APK SHA-256
                if (!stopped && apkOk) {
                    String hash = sha256(apk);

                    report.append("\nAPK SHA-256:\n")
                        .append(hash)
                        .append("\n");

                    logEvent("APK_SHA256_CALCULATED", hash);
                } else if (!apkOk) {
                    report.append("APK hash unavailable.\n");
                }

                if (stopped) {
                    return;
                }

                // 5. Debug build indicator
                boolean debugBuild =
                    (getApplicationInfo().flags
                    & ApplicationInfo.FLAG_DEBUGGABLE) != 0;

                report.append("\n[")
                    .append(debugBuild ? "INFO" : "PASS")
                    .append("] Debuggable build: ")
                    .append(debugBuild)
                    .append("\n");

                if (debugBuild) {
                    warnings++;
                    logEvent("DEBUG_BUILD_NOTICE",
                        "Application is debuggable");
                } else {
                    passed++;
                    logEvent("DEBUG_BUILD_CHECK", "PASS");
                }

                report.append("\nChecks passed: ")
                    .append(passed)
                    .append("\nNot passed / notices: ")
                    .append(warnings)
                    .append("\n\n");

                report.append(
                    "IMPORTANT:\n" +
                    "The SHA-256 value is informational. " +
                    "Without a trusted baseline, it does not " +
                    "prove that the APK is authentic.\n"
                );

                logEvent("DIAGNOSTICS_COMPLETED",
                    "Passed=" + passed + ", notices=" + warnings);

                final String finalReport = report.toString();
                final int finalPassed = passed;
                final int finalWarnings = warnings;

                runOnUiThread(() -> {
                    if (stopped) {
                        return;
                    }

                    resultView.setText(finalReport);
                    statusView.setText(
                        finalWarnings == 0
                            ? "ПРОВЕРКИ ЗАВЕРШЕНЫ"
                            : "ПРОВЕРКИ ЗАВЕРШЕНЫ С ЗАМЕЧАНИЯМИ"
                    );
                    statusView.setBackgroundColor(
                        finalWarnings == 0 ? green : blue
                    );
                    logView.setText(
                        "Успешно: " + finalPassed +
                        " | Замечания: " + finalWarnings
                    );
                    checking = false;
                });

            } catch (Exception e) {
                logEvent("DIAGNOSTICS_ERROR",
                    e.getClass().getSimpleName());

                runOnUiThread(() -> {
                    if (!stopped) {
                        statusView.setText("ОШИБКА ДИАГНОСТИКИ");
                        statusView.setBackgroundColor(
                            Color.rgb(170, 55, 55)
                        );
                        resultView.setText(
                            "Проверка завершилась ошибкой: " +
                            e.getClass().getSimpleName()
                        );
                    }
                    checking = false;
                });
            }
        }).start();
    }

    private String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (InputStream input = new BufferedInputStream(
                new FileInputStream(file))) {

            byte[] buffer = new byte[8192];
            int count;

            while ((count = input.read(buffer)) != -1) {
                if (stopped) {
                    return "CANCELLED";
                }
                digest.update(buffer, 0, count);
            }
        }

        byte[] bytes = digest.digest();
        StringBuilder hex = new StringBuilder();

        for (byte b : bytes) {
            hex.append(String.format(Locale.US, "%02x", b & 0xff));
        }

        return hex.toString();
    }

    private void emergencyStop() {
        stopped = true;
        checking = false;

        statusView.setText("АГЕНТ ОСТАНОВЛЕН");
        statusView.setBackgroundColor(Color.rgb(170, 55, 55));
        resultView.setText(
            "Новые проверки остановлены.\n" +
            "Нажми «Запустить проверку», чтобы продолжить."
        );

        logEvent("EMERGENCY_STOP", "User stopped diagnostics");
        logView.setText("Диагностика остановлена пользователем.");
    }

    private void logEvent(String event, String details) {
        String time = new SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            Locale.US
        ).format(new Date());

        String safeDetails = details == null
            ? ""
            : details.replace("\n", " ").replace("\r", " ");

        String line = time + " | " + event + " | "
            + safeDetails + "\n";

        try (FileOutputStream output = new FileOutputStream(
                new File(getFilesDir(), "asa-events.log"), true)) {

            output.write(line.getBytes("UTF-8"));
        } catch (Exception ignored) {
            // Logging failure must not crash the app.
        }
    }

    private void showLogs() {
        File file = new File(getFilesDir(), "asa-events.log");

        if (!file.exists()) {
            logView.setText("Журнал пока пуст.");
            return;
        }

        StringBuilder contents = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                    new FileInputStream(file), "UTF-8"))) {

            String line;
            while ((line = reader.readLine()) != null) {
                contents.append(line).append("\n");
            }

            logView.setText(contents.toString());
            logEvent("LOG_VIEWED", "Local event log opened");

        } catch (Exception e) {
            logView.setText("Не удалось прочитать журнал.");
        }
    }
}
