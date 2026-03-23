package com.pakgopay.demo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.table.DefaultTableModel;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.stream.Collectors;
import javax.swing.Scrollable;

public class MerchantDemoApp {
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private static final Gson PRETTY_GSON = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final Color COLOR_BG = new Color(246, 248, 252);
    private static final Color COLOR_PANEL = new Color(255, 255, 255);
    private static final Color COLOR_BORDER = new Color(225, 231, 242);
    private static final Color COLOR_TEXT = new Color(31, 41, 55);
    private static final Color COLOR_TEXT_MUTED = new Color(100, 116, 139);
    private static final Color COLOR_ACCENT = new Color(37, 99, 235);
    private static final Color COLOR_ACCENT_DARK = new Color(29, 78, 216);
    private static final Color COLOR_TAB_BG = new Color(238, 242, 255);
    private static final Font FONT_NORMAL = new Font("SansSerif", Font.PLAIN, 13);
    private static final Font FONT_TITLE = new Font("SansSerif", Font.BOLD, 13);
    private static final Font FONT_MONO = new Font("Monospaced", Font.PLAIN, 13);
    private static final DateTimeFormatter MERCHANT_ORDER_NO_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy_MM_dd_HH:mm:ss");

    private final ConfigStore configStore = new ConfigStore();
    private final HttpClientUtil httpClient = new HttpClientUtil();

    private JComboBox<String> baseUrlCombo;
    private JTextField tokenField;
    private JTextField signKeyField;
    private JComboBox<Language> languageCombo;
    private JComboBox<ApiEndpoint> endpointCombo;
    private JTextArea endpointCommentArea;

    private JPanel dynamicParamPanel;
    private final List<ParamFieldBinding> currentParamBindings = new ArrayList<>();
    private JTextArea requestBodyArea;

    private JTextArea responseArea;
    private DefaultTableModel tableModel;

    private JPanel configPanel;
    private JPanel queryPanel;
    private JScrollPane endpointCommentScroll;
    private JScrollPane queryPanelScroll;
    private JScrollPane requestBodyScroll;
    private JScrollPane parsedResultScroll;
    private JScrollPane responseScroll;
    private JTabbedPane requestResponseTabs;
    private JSplitPane centerSplitPane;

    private JLabel endpointLabel;
    private JLabel authLabel;
    private JLabel signKeyLabel;
    private JLabel languageLabel;

    private JButton saveBtn;
    private JButton loadBtn;
    private JButton buildBodyBtn;
    private JButton sendBtn;

    private Language currentLanguage = Language.EN;
    private AppConfig appConfig;
    private String currentEndpointPath;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MerchantDemoApp().show());
    }

    private void show() {
        applyGlobalTheme();
        JFrame frame = new JFrame("Merchant Demo (Swing)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1320, 860);
        frame.setMinimumSize(new Dimension(1200, 760));
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        root.setBackground(COLOR_BG);

        root.add(buildConfigPanel(), BorderLayout.NORTH);
        root.add(buildCenterPanel(), BorderLayout.CENTER);

        frame.setContentPane(root);
        loadConfigToUi();
        applyLanguageTexts();
        frame.setVisible(true);
    }

    private JPanel buildConfigPanel() {
        configPanel = new JPanel(new GridBagLayout());
        setCardBorder(configPanel, "Connection");
        configPanel.setBackground(COLOR_PANEL);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;

        baseUrlCombo = new JComboBox<>(new String[]{
                "http://127.0.0.1:8080",
                "https://wanwanpay.com/api"
        });
        baseUrlCombo.setEditable(true);
        tokenField = new JTextField();
        signKeyField = new JTextField();
        styleComboBox(baseUrlCombo);
        styleField(tokenField);
        styleField(signKeyField);

        languageCombo = new JComboBox<>(Language.values());
        styleComboBox(languageCombo);
        languageCombo.addActionListener(e -> onLanguageChanged());

        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0;
        configPanel.add(new JLabel("Base URL"), c);
        c.gridx = 1;
        c.gridy = 0;
        c.weightx = 1;
        configPanel.add(baseUrlCombo, c);

        languageLabel = new JLabel("Language");
        c.gridx = 2;
        c.gridy = 0;
        c.weightx = 0;
        configPanel.add(languageLabel, c);
        c.gridx = 3;
        c.gridy = 0;
        c.weightx = 0.4;
        configPanel.add(languageCombo, c);

        endpointCombo = new JComboBox<>(buildEndpoints());
        styleComboBox(endpointCombo);
        endpointCombo.addActionListener(e -> onEndpointChanged());
        endpointLabel = new JLabel("API Endpoint");
        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0;
        configPanel.add(endpointLabel, c);
        c.gridx = 1;
        c.gridy = 1;
        c.gridwidth = 4;
        c.weightx = 1;
        configPanel.add(endpointCombo, c);
        c.gridwidth = 1;

        authLabel = new JLabel("api-key");
        c.gridx = 0;
        c.gridy = 2;
        c.weightx = 0;
        configPanel.add(authLabel, c);
        c.gridx = 1;
        c.gridy = 2;
        c.gridwidth = 1;
        c.weightx = 1;
        configPanel.add(tokenField, c);

        signKeyLabel = new JLabel("signKey");
        c.gridx = 2;
        c.gridy = 2;
        c.weightx = 0;
        configPanel.add(signKeyLabel, c);
        c.gridx = 3;
        c.gridy = 2;
        c.weightx = 1;
        c.gridwidth = 2;
        configPanel.add(signKeyField, c);
        c.gridwidth = 1;

        endpointCommentArea = new JTextArea(3, 30);
        endpointCommentArea.setLineWrap(true);
        endpointCommentArea.setWrapStyleWord(true);
        endpointCommentArea.setEditable(false);
        endpointCommentArea.setBackground(new Color(248, 251, 255));
        endpointCommentArea.setForeground(COLOR_TEXT_MUTED);
        endpointCommentArea.setFont(FONT_NORMAL);
        endpointCommentScroll = new JScrollPane(endpointCommentArea);
        endpointCommentScroll.setBorder(buildCardBorder("Endpoint Note"));
        endpointCommentScroll.getViewport().setBackground(new Color(248, 251, 255));
        c.gridx = 0;
        c.gridy = 3;
        c.gridwidth = 5;
        c.weightx = 1;
        configPanel.add(endpointCommentScroll, c);
        c.gridwidth = 1;

        return configPanel;
    }

    private JSplitPane buildCenterPanel() {
        queryPanel = new WidthTrackingPanel(new GridBagLayout());
        setCardBorder(queryPanel, "Order Query");
        queryPanel.setBackground(COLOR_PANEL);

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;

        dynamicParamPanel = new JPanel(new GridBagLayout());
        setCardBorder(dynamicParamPanel, "Request Params");
        dynamicParamPanel.setBackground(COLOR_PANEL);
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 4;
        c.weightx = 1;
        queryPanel.add(dynamicParamPanel, c);
        c.gridwidth = 1;

        loadBtn = new JButton("Reload Config");
        loadBtn.addActionListener(e -> handleReloadConfigAction());
        saveBtn = new JButton("Save Config");
        saveBtn.addActionListener(e -> saveConfigFromUi());
        buildBodyBtn = new JButton("Build Query Body");
        buildBodyBtn.addActionListener(e -> buildQueryBodyToEditor());
        sendBtn = new JButton("Send");
        sendBtn.addActionListener(e -> sendRequest());
        styleButton(loadBtn, false);
        styleButton(saveBtn, false);
        styleButton(buildBodyBtn, true);
        styleButton(sendBtn, true);
        Dimension actionButtonSize = new Dimension(0, 56);
        loadBtn.setPreferredSize(actionButtonSize);
        saveBtn.setPreferredSize(actionButtonSize);
        buildBodyBtn.setPreferredSize(actionButtonSize);
        sendBtn.setPreferredSize(actionButtonSize);
        // Reduce internal padding to avoid text clipping in default window size.
        Insets compactMargin = new Insets(2, 6, 2, 6);
        loadBtn.setMargin(compactMargin);
        saveBtn.setMargin(compactMargin);
        buildBodyBtn.setMargin(compactMargin);
        sendBtn.setMargin(compactMargin);

        JPanel actionPanel = new JPanel(new GridLayout(2, 2, 12, 8));
        actionPanel.add(loadBtn);
        actionPanel.add(saveBtn);
        actionPanel.add(buildBodyBtn);
        actionPanel.add(sendBtn);
        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 4;
        c.weightx = 1;
        c.fill = GridBagConstraints.BOTH;
        queryPanel.add(actionPanel, c);
        c.gridwidth = 1;
        c.fill = GridBagConstraints.HORIZONTAL;

        requestBodyArea = new JTextArea(10, 80);
        requestBodyArea.setLineWrap(true);
        requestBodyArea.setWrapStyleWord(true);
        requestBodyArea.setFont(FONT_MONO);
        requestBodyArea.setForeground(COLOR_TEXT);
        requestBodyScroll = new JScrollPane(requestBodyArea);
        requestBodyScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        requestBodyScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        requestBodyScroll.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        requestBodyScroll.getViewport().setBackground(new Color(251, 253, 255));

        responseArea = new JTextArea(10, 120);
        responseArea.setLineWrap(true);
        responseArea.setWrapStyleWord(true);
        responseArea.setFont(FONT_MONO);
        responseArea.setForeground(COLOR_TEXT);
        responseScroll = new JScrollPane(responseArea);
        responseScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        responseScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        responseScroll.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        responseScroll.getViewport().setBackground(new Color(251, 253, 255));

        requestResponseTabs = new JTabbedPane();
        requestResponseTabs.setTabPlacement(JTabbedPane.TOP);
        requestResponseTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        requestResponseTabs.setBorder(new LineBorder(COLOR_BORDER, 1, true));
        requestResponseTabs.setOpaque(true);
        requestResponseTabs.setBackground(COLOR_TAB_BG);
        requestResponseTabs.setFont(FONT_NORMAL);
        requestResponseTabs.setUI(new BasicTabbedPaneUI() {
            @Override
            protected void paintTabBackground(
                    Graphics g, int tabPlacement, int tabIndex,
                    int x, int y, int w, int h, boolean isSelected) {
                g.setColor(isSelected ? COLOR_PANEL : COLOR_TAB_BG);
                g.fillRect(x, y, w, h);
            }

            @Override
            protected void paintTabBorder(
                    Graphics g, int tabPlacement, int tabIndex,
                    int x, int y, int w, int h, boolean isSelected) {
                g.setColor(COLOR_BORDER);
                g.drawRect(x, y, w, h);
            }

            @Override
            protected void paintFocusIndicator(
                    Graphics g, int tabPlacement, Rectangle[] rects, int tabIndex,
                    Rectangle iconRect, Rectangle textRect, boolean isSelected) {
                // Flat style: no focus indicator drawing.
            }

            @Override
            protected Insets getTabInsets(int tabPlacement, int tabIndex) {
                return new Insets(6, 12, 6, 12);
            }

            @Override
            protected Insets getTabAreaInsets(int tabPlacement) {
                return new Insets(0, 0, 0, 0);
            }

            @Override
            protected Insets getContentBorderInsets(int tabPlacement) {
                return new Insets(1, 1, 1, 1);
            }
        });
        requestResponseTabs.addTab("Request JSON", requestBodyScroll);
        requestResponseTabs.addTab("Raw Response", responseScroll);
        requestResponseTabs.setSelectedIndex(0);
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 4;
        c.weightx = 1;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        queryPanel.add(requestResponseTabs, c);

        tableModel = new DefaultTableModel(new Object[]{"key", "value"}, 0);
        JTable table = new JTable(tableModel);
        table.setRowHeight(24);
        table.setGridColor(new Color(233, 238, 247));
        table.setFont(FONT_NORMAL);
        table.getTableHeader().setFont(FONT_TITLE);
        table.getTableHeader().setBackground(new Color(241, 246, 255));
        table.getTableHeader().setForeground(COLOR_TEXT);
        parsedResultScroll = new JScrollPane(table);
        parsedResultScroll.setBorder(buildCardBorder("Parsed Result"));
        parsedResultScroll.getViewport().setBackground(COLOR_PANEL);

        queryPanelScroll = new JScrollPane(queryPanel);
        queryPanelScroll.setBorder(null);
        queryPanelScroll.getVerticalScrollBar().setUnitIncrement(16);
        queryPanelScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        queryPanelScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        queryPanelScroll.getViewport().setBackground(COLOR_BG);
        installNestedScrollBridge(requestBodyScroll, queryPanelScroll);
        installNestedScrollBridge(responseScroll, queryPanelScroll);

        centerSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, queryPanelScroll, parsedResultScroll);
        centerSplitPane.setResizeWeight(1.0);
        centerSplitPane.setBorder(BorderFactory.createEmptyBorder());
        toggleParsedResult(false);
        return centerSplitPane;
    }

    private void loadConfigToUi() {
        appConfig = configStore.load();
        if (appConfig.getEndpointParamConfig() == null) {
            appConfig.setEndpointParamConfig(new LinkedHashMap<>());
        }
        setSelectedBaseUrl(appConfig.getBaseUrl());
        tokenField.setText(stripApiKeyPrefix(appConfig.getToken()));
        signKeyField.setText(appConfig.getSignKey() == null ? "" : appConfig.getSignKey());
        ApiEndpoint selected = endpointByPath(appConfig.getQueryPath());
        endpointCombo.setSelectedItem(selected == null ? buildEndpoints()[0] : selected);
        onEndpointChanged();
    }

    private void saveConfigFromUi() {
        try {
            if (appConfig == null) {
                appConfig = AppConfig.defaults();
            }
            appConfig.setBaseUrl(getSelectedBaseUrl());
            appConfig.setToken(tokenField.getText() == null ? "" : tokenField.getText().trim());
            appConfig.setSignKey(signKeyField.getText() == null ? "" : signKeyField.getText().trim());
            ApiEndpoint endpoint = (ApiEndpoint) endpointCombo.getSelectedItem();
            String endpointPath = endpoint == null ? "" : endpoint.path;
            appConfig.setQueryPath(endpointPath);
            if (appConfig.getEndpointParamConfig() == null) {
                appConfig.setEndpointParamConfig(new LinkedHashMap<>());
            }
            appConfig.getEndpointParamConfig().put(endpointPath, collectCurrentParamValues());
            configStore.save(appConfig);
            JOptionPane.showMessageDialog(null, currentLanguage == Language.ZH ? "已保存" : "Saved");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    null,
                    (currentLanguage == Language.ZH ? "保存失败: " : "Save failed: ") + ex.getMessage());
        }
    }

    private void handleReloadConfigAction() {
        String title = currentLanguage == Language.ZH ? "重载配置" : "Reload Config";
        String message = currentLanguage == Language.ZH
                ? "请选择重置范围"
                : "Select reset scope";
        String[] options = currentLanguage == Language.ZH
                ? new String[]{"重置当前接口", "重置所有接口", "取消"}
                : new String[]{"Reset Current Endpoint", "Reset All Endpoints", "Cancel"};
        int choice = JOptionPane.showOptionDialog(
                null,
                message,
                title,
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);
        if (choice == 0) {
            resetCurrentEndpointConfig();
        } else if (choice == 1) {
            resetAllConfig();
        }
    }

    private void resetCurrentEndpointConfig() {
        try {
            if (appConfig == null) {
                appConfig = AppConfig.defaults();
            }
            ApiEndpoint endpoint = (ApiEndpoint) endpointCombo.getSelectedItem();
            if (endpoint != null && appConfig.getEndpointParamConfig() != null) {
                appConfig.getEndpointParamConfig().remove(endpoint.path);
            }
            configStore.save(appConfig);
            onEndpointChanged();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    null,
                    (currentLanguage == Language.ZH ? "重置失败: " : "Reset failed: ") + ex.getMessage());
        }
    }

    private void resetAllConfig() {
        try {
            appConfig = AppConfig.defaults();
            configStore.save(appConfig);
            loadConfigToUi();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    null,
                    (currentLanguage == Language.ZH ? "重置失败: " : "Reset failed: ") + ex.getMessage());
        }
    }

    private void sendRequest() {
        AppConfig cfg = new AppConfig();
        cfg.setBaseUrl(getSelectedBaseUrl());
        cfg.setToken(normalizeApiKey(tokenField.getText()));
        ApiEndpoint endpoint = (ApiEndpoint) endpointCombo.getSelectedItem();
        cfg.setQueryPath(endpoint == null ? "" : endpoint.path);

        String requestJson = requestBodyArea.getText();
        String finalAuthorization = cfg.getToken();
        String finalRequestJson = "{}";
        try {
            JsonObject root = requestJson == null || requestJson.isBlank()
                    ? new JsonObject()
                    : GSON.fromJson(requestJson, JsonObject.class);
            if (root != null && root.has("headers") && root.get("headers").isJsonObject()) {
                JsonObject headers = root.getAsJsonObject("headers");
                if (headers.has("Authorization") && !headers.get("Authorization").isJsonNull()) {
                    finalAuthorization = String.valueOf(headers.get("Authorization").getAsString());
                }
            }
            if (root != null && root.has("body") && root.get("body").isJsonObject()) {
                finalRequestJson = GSON.toJson(root.getAsJsonObject("body"));
            } else if (root != null) {
                finalRequestJson = GSON.toJson(root);
            }
        } catch (Exception e) {
            finalRequestJson = (requestJson == null || requestJson.isBlank()) ? "{}" : requestJson;
        }

        String url = cfg.getBaseUrl() + cfg.getQueryPath();
        final boolean shouldTryAutoOpen = endpoint != null
                && ("/pakGoPay/api/server/v1/createCollectionOrder".equals(endpoint.path)
                || "/pakGoPay/api/server/v1/createPayOutOrder".equals(endpoint.path));
        final String sendAuthorization = finalAuthorization;
        final String sendBody = finalRequestJson;
        responseArea.setText(
                (currentLanguage == Language.ZH ? "请求地址: " : "Request URL: ") + url
                        + "\n"
                        + (currentLanguage == Language.ZH ? "请求体: " : "Request Body: ")
                        + sendBody
                        + "\n\n"
                        + (currentLanguage == Language.ZH ? "请求中..." : "Loading..."));
        requestResponseTabs.setSelectedIndex(1);

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                return httpClient.postJson(url, sendAuthorization, sendBody);
            }

            @Override
            protected void done() {
                try {
                    String text = get();
                    responseArea.setText(formatRawResponse(text));
                    fillTableFromResponse(text);
                    if (shouldTryAutoOpen) {
                        String link = extractPaymentUrlFromResponse(text);
                        openBrowserIfNeeded(link);
                    }
                } catch (Exception ex) {
                    responseArea.setText(
                            (currentLanguage == Language.ZH ? "请求失败: " : "Request failed: ") + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    private String extractPaymentUrlFromResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int idx = raw.indexOf('\n');
        if (idx < 0 || idx + 1 >= raw.length()) {
            return null;
        }
        String body = raw.substring(idx + 1).trim();
        try {
            JsonObject root = GSON.fromJson(body, JsonObject.class);
            JsonElement data = extractDataElement(root);
            if (data == null || data.isJsonNull()) {
                return null;
            }
            return findPreferredUrl(data);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String findPreferredUrl(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            String[] preferredKeys = new String[]{
                    "payUrl", "url", "cashierUrl", "checkoutUrl", "redirectUrl", "qrCodeUrl", "appUrl"
            };
            for (String key : preferredKeys) {
                String value = readStringIfUrl(obj.get(key));
                if (value != null) {
                    return value;
                }
            }
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                String value = findPreferredUrl(entry.getValue());
                if (value != null) {
                    return value;
                }
            }
            return null;
        }
        if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                String value = findPreferredUrl(item);
                if (value != null) {
                    return value;
                }
            }
            return null;
        }
        return readStringIfUrl(element);
    }

    private String readStringIfUrl(JsonElement value) {
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return null;
        }
        String text = value.getAsString();
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        return null;
    }

    private void openBrowserIfNeeded(String link) {
        if (link == null || link.isBlank()) {
            return;
        }
        try {
            if (!Desktop.isDesktopSupported()) {
                return;
            }
            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.BROWSE)) {
                return;
            }
            desktop.browse(URI.create(link));
        } catch (Exception ignored) {
            // Ignore browser open failure, keep UI flow unchanged.
        }
    }

    private String formatRawResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        int idx = raw.indexOf('\n');
        if (idx < 0 || idx + 1 >= raw.length()) {
            return raw;
        }
        String head = raw.substring(0, idx).trim();
        String body = raw.substring(idx + 1).trim();
        if (body.isBlank()) {
            return head;
        }
        try {
            JsonElement parsed = GSON.fromJson(body, JsonElement.class);
            JsonElement normalized = normalizeEscapedDataJson(parsed);
            String pretty = PRETTY_GSON.toJson(normalized);
            return head + "\n" + pretty;
        } catch (Exception ignored) {
            return raw;
        }
    }

    /**
     * If response JSON has data as escaped JSON string, convert it to real JSON for display.
     */
    private JsonElement normalizeEscapedDataJson(JsonElement root) {
        if (root == null || !root.isJsonObject()) {
            return root;
        }
        JsonObject obj = root.getAsJsonObject();
        JsonElement data = obj.get("data");
        if (data == null || data.isJsonNull()) {
            return root;
        }
        if (data.isJsonPrimitive() && data.getAsJsonPrimitive().isString()) {
            String raw = data.getAsString();
            if (raw != null && !raw.isBlank()) {
                try {
                    JsonElement parsedData = GSON.fromJson(raw, JsonElement.class);
                    obj.add("data", parsedData);
                } catch (Exception ignored) {
                    // keep original string when it is not a JSON string payload
                }
            }
        }
        return obj;
    }

    private void buildQueryBodyToEditor() {
        ApiEndpoint endpoint = (ApiEndpoint) endpointCombo.getSelectedItem();
        JsonObject body = new JsonObject();
        for (ParamFieldBinding binding : currentParamBindings) {
            String value = binding.input.getText() == null ? "" : binding.input.getText().trim();
            if (value.isEmpty()) {
                continue;
            }
            switch (binding.field.type) {
                case NUMBER -> putNumberOrString(body, binding.field.key, value);
                case BOOLEAN -> putBooleanOrString(body, binding.field.key, value);
                default -> body.addProperty(binding.field.key, value);
            }
        }
        if (endpoint != null) {
            applyAutoSign(endpoint.path, body);
        }

        JsonObject headers = new JsonObject();
        headers.addProperty("Authorization", normalizeApiKey(tokenField.getText()));
        JsonObject root = new JsonObject();
        String url = "";
        if (endpoint != null) {
            url = getSelectedBaseUrl() + endpoint.path;
        }
        root.addProperty("url", url);
        root.add("headers", headers);
        root.add("body", body);
        requestBodyArea.setText(PRETTY_GSON.toJson(root));
        requestResponseTabs.setSelectedIndex(0);
    }

    private void onEndpointChanged() {
        ApiEndpoint endpoint = (ApiEndpoint) endpointCombo.getSelectedItem();
        if (endpoint == null) {
            return;
        }
        currentEndpointPath = endpoint.path;
        endpointCommentArea.setText(endpoint.comment);
        rebuildDynamicParamPanel(endpoint);
        applySavedParamValues(endpoint.path);
        buildQueryBodyToEditor();
        toggleParsedResult(false);
    }

    private void rebuildDynamicParamPanel(ApiEndpoint endpoint) {
        dynamicParamPanel.removeAll();
        currentParamBindings.clear();

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        for (int i = 0; i < endpoint.fields.length; i++) {
            ParamField field = endpoint.fields[i];
            addSingleField(field, row, c);
            row++;
        }

        dynamicParamPanel.revalidate();
        dynamicParamPanel.repaint();
    }

    private void addSingleField(ParamField field, int row, GridBagConstraints c) {
        JLabel label = new JLabel(currentLanguage == Language.ZH ? field.labelZh : field.labelEn);
        label.setForeground(COLOR_TEXT);
        JTextField input = new JTextField(field.defaultValue == null ? "" : field.defaultValue);
        styleField(input);
        boolean showAutoGenBtn = "merchantOrderNo".equals(field.key)
                && ("/pakGoPay/api/server/v1/createCollectionOrder".equals(currentEndpointPath)
                || "/pakGoPay/api/server/v1/createPayOutOrder".equals(currentEndpointPath));
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        dynamicParamPanel.add(label, c);
        c.gridx = 1;
        c.gridy = row;
        c.weightx = 1;
        c.gridwidth = showAutoGenBtn ? 2 : 3;
        dynamicParamPanel.add(input, c);
        c.gridwidth = 1;

        if (showAutoGenBtn) {
            JButton autoGenBtn = new JButton(currentLanguage == Language.ZH ? "自动生成" : "Auto Gen");
            styleButton(autoGenBtn, false);
            autoGenBtn.addActionListener(e -> input.setText(generateMerchantOrderNo()));
            c.gridx = 3;
            c.gridy = row;
            c.weightx = 0;
            dynamicParamPanel.add(autoGenBtn, c);
        }

        currentParamBindings.add(new ParamFieldBinding(field, input));
    }

    private String generateMerchantOrderNo() {
        return "test" + LocalDateTime.now().format(MERCHANT_ORDER_NO_TIME_FORMAT);
    }

    private void onLanguageChanged() {
        Language selected = (Language) languageCombo.getSelectedItem();
        if (selected == null || selected == currentLanguage) {
            return;
        }
        currentLanguage = selected;
        ApiEndpoint old = (ApiEndpoint) endpointCombo.getSelectedItem();
        String oldPath = old == null ? null : old.path;
        endpointCombo.setModel(new DefaultComboBoxModel<>(buildEndpoints()));
        ApiEndpoint selectedEndpoint = endpointByPath(oldPath);
        endpointCombo.setSelectedItem(selectedEndpoint == null ? buildEndpoints()[0] : selectedEndpoint);
        applyLanguageTexts();
        onEndpointChanged();
    }

    private ApiEndpoint endpointByPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String target = path.trim();
        for (int i = 0; i < endpointCombo.getItemCount(); i++) {
            ApiEndpoint endpoint = endpointCombo.getItemAt(i);
            if (endpoint.path.equals(target)) {
                return endpoint;
            }
        }
        return null;
    }

    private ApiEndpoint[] buildEndpoints() {
        if (currentLanguage == Language.ZH) {
            return new ApiEndpoint[]{
                    new ApiEndpoint(
                            "查询订单",
                            "/pakGoPay/api/server/v1/queryOrder",
                            "按 orderType + transactionNo / merchantOrderNo 查询单笔订单状态。",
                            new ParamField[]{
                                    new ParamField("merchantId", "Merchant ID", "商户ID", "", ParamType.STRING),
                                    new ParamField("merchantOrderNo", "Merchant Order No", "商户订单号", "", ParamType.STRING),
                                    new ParamField("orderType", "Order Type", "订单类型", "COLL", ParamType.STRING),
                            }),
                    new ApiEndpoint(
                            "创建代收订单",
                            "/pakGoPay/api/server/v1/createCollectionOrder",
                            "创建商户代收订单（对外接口）。",
                            new ParamField[]{
                                    new ParamField("merchantId", "Merchant ID", "商户ID", "", ParamType.STRING),
                                    new ParamField("merchantOrderNo", "Merchant Order No", "商户订单号", "", ParamType.STRING),
                                    new ParamField("amount", "Amount", "金额", "100.00", ParamType.NUMBER),
                                    new ParamField("currency", "Currency", "币种", "CNY", ParamType.STRING),
                                    new ParamField("paymentNo", "Payment No", "通道编号", "alipay", ParamType.STRING),
                                    new ParamField("notificationUrl", "Notification URL", "通知地址", "https://example.com/notify", ParamType.STRING),
                                    new ParamField("clientIp", "Client IP", "客户端IP", "127.0.0.1", ParamType.STRING),
                                    new ParamField("remark", "Remark", "备注", "", ParamType.STRING)
                            }),
                    new ApiEndpoint(
                            "创建代付订单",
                            "/pakGoPay/api/server/v1/createPayOutOrder",
                            "创建商户代付订单（对外接口）。",
                            new ParamField[]{
                                    new ParamField("merchantId", "Merchant ID", "商户ID", "", ParamType.STRING),
                                    new ParamField("merchantOrderNo", "Merchant Order No", "商户订单号", "", ParamType.STRING),
                                    new ParamField("amount", "Amount", "金额", "100.00", ParamType.NUMBER),
                                    new ParamField("currency", "Currency", "币种", "CNY", ParamType.STRING),
                                    new ParamField("paymentNo", "Payment No", "通道编号", "alipay", ParamType.STRING),
                                    new ParamField("notificationUrl", "Notification URL", "通知地址", "https://example.com/notify", ParamType.STRING),
                                    new ParamField("clientIp", "Client IP", "客户端IP", "127.0.0.1", ParamType.STRING),
                                    new ParamField("remark", "Remark", "备注", "", ParamType.STRING),
                                    new ParamField("bankCode", "Bank Code", "银行编码", "", ParamType.STRING),
                                    new ParamField("accountName", "Account Name", "账户名", "", ParamType.STRING),
                                    new ParamField("accountNo", "Account No", "账号", "", ParamType.STRING)
                            }),
                    new ApiEndpoint(
                            "查询余额",
                            "/pakGoPay/api/server/v1/balance",
                            "按币种查询商户余额。",
                            new ParamField[]{
                                    new ParamField("merhcantId", "Merchant ID", "商户ID", "", ParamType.STRING),
                            })
            };
        }

        return new ApiEndpoint[]{
                new ApiEndpoint(
                        "Query Order",
                        "/pakGoPay/api/server/v1/queryOrder",
                        "Query single order status by orderType + transactionNo / merchantOrderNo.",
                        new ParamField[]{
                                new ParamField("merchantId", "Merchant ID", "商户ID", "", ParamType.STRING),
                                new ParamField("merchantOrderNo", "Merchant Order No", "商户订单号", "", ParamType.STRING),
                                new ParamField("orderType", "Order Type", "订单类型", "COLL", ParamType.STRING),
                        }),
                new ApiEndpoint(
                        "Create Collection Order",
                        "/pakGoPay/api/server/v1/createCollectionOrder",
                        "Create merchant collection order (external API).",
                        new ParamField[]{
                                new ParamField("merchantId", "Merchant ID", "商户ID", "", ParamType.STRING),
                                new ParamField("merchantOrderNo", "Merchant Order No", "商户订单号", "", ParamType.STRING),
                                new ParamField("amount", "Amount", "金额", "100.00", ParamType.NUMBER),
                                new ParamField("currency", "Currency", "币种", "CNY", ParamType.STRING),
                                new ParamField("paymentNo", "Payment No", "通道编号", "alipay", ParamType.STRING),
                                new ParamField("notificationUrl", "Notification URL", "通知地址", "https://example.com/notify", ParamType.STRING),
                                new ParamField("clientIp", "Client IP", "客户端IP", "127.0.0.1", ParamType.STRING),
                                new ParamField("remark", "Remark", "备注", "", ParamType.STRING)
                        }),
                new ApiEndpoint(
                        "Create Payout Order",
                        "/pakGoPay/api/server/v1/createPayOutOrder",
                        "Create merchant payout order (external API).",
                        new ParamField[]{
                                new ParamField("merchantId", "Merchant ID", "商户ID", "", ParamType.STRING),
                                new ParamField("merchantOrderNo", "Merchant Order No", "商户订单号", "", ParamType.STRING),
                                new ParamField("amount", "Amount", "金额", "100.00", ParamType.NUMBER),
                                new ParamField("currency", "Currency", "币种", "CNY", ParamType.STRING),
                                new ParamField("paymentNo", "Payment No", "通道编号", "alipay", ParamType.STRING),
                                new ParamField("notificationUrl", "Notification URL", "通知地址", "https://example.com/notify", ParamType.STRING),
                                new ParamField("clientIp", "Client IP", "客户端IP", "127.0.0.1", ParamType.STRING),
                                new ParamField("remark", "Remark", "备注", "", ParamType.STRING),
                                new ParamField("bankCode", "Bank Code", "银行编码", "", ParamType.STRING),
                                new ParamField("accountName", "Account Name", "账户名", "", ParamType.STRING),
                                new ParamField("accountNo", "Account No", "账号", "", ParamType.STRING)
                        }),
                new ApiEndpoint(
                        "Query Balance",
                        "/pakGoPay/api/server/v1/balance",
                        "Query merchant balance by currency.",
                        new ParamField[]{
                                new ParamField("merhcantId", "Merchant ID", "商户ID", "", ParamType.STRING),
                        })
        };
    }

    private void applyLanguageTexts() {
        boolean zh = currentLanguage == Language.ZH;
        setTitle(configPanel, zh ? "连接配置" : "Connection");
        setTitle(queryPanel, zh ? "订单请求" : "Order Request");
        setTitle(dynamicParamPanel, zh ? "请求参数" : "Request Params");
        setTitle(endpointCommentScroll, zh ? "接口说明" : "Endpoint Note");
        setTitle(requestBodyScroll, zh ? "请求 JSON" : "Request JSON");
        setTitle(parsedResultScroll, zh ? "解析结果" : "Parsed Result");
        setTitle(responseScroll, zh ? "原始响应" : "Raw Response");
        requestResponseTabs.setTitleAt(0, zh ? "请求JSON" : "Request JSON");
        requestResponseTabs.setTitleAt(1, zh ? "原始响应" : "Raw Response");

        endpointLabel.setText(zh ? "接口选择" : "API Endpoint");
        authLabel.setText("api-key");
        signKeyLabel.setText("signKey");
        languageLabel.setText(zh ? "语言" : "Language");
        saveBtn.setText(zh ? "保存配置" : "Save Config");
        loadBtn.setText(zh ? "重载配置" : "Reload Config");
        buildBodyBtn.setText(zh ? "生成请求体" : "Build Query Body");
        sendBtn.setText(zh ? "发送" : "Send");
    }

    private void setTitle(JComponent component, String title) {
        if (component == null || component.getBorder() == null) {
            return;
        }
        if (component.getBorder() instanceof TitledBorder border) {
            border.setTitle(title);
            border.setTitleFont(FONT_TITLE);
            border.setTitleColor(COLOR_TEXT);
            component.repaint();
        }
    }

    private void applyGlobalTheme() {
        UIManager.put("Label.font", FONT_NORMAL);
        UIManager.put("Label.foreground", COLOR_TEXT);
        UIManager.put("TextField.font", FONT_NORMAL);
        UIManager.put("TextArea.font", FONT_NORMAL);
        UIManager.put("ComboBox.font", FONT_NORMAL);
        UIManager.put("Button.font", FONT_NORMAL);
        UIManager.put("Panel.background", COLOR_PANEL);
        UIManager.put("Table.font", FONT_NORMAL);
    }

    private void styleField(JTextField field) {
        field.setFont(FONT_NORMAL);
        field.setForeground(COLOR_TEXT);
        field.setBackground(COLOR_PANEL);
        field.setCaretColor(COLOR_TEXT);
        field.setBorder(new CompoundBorder(
                new LineBorder(COLOR_BORDER, 1, true),
                new EmptyBorder(6, 8, 6, 8)));
    }

    private void styleComboBox(JComboBox<?> comboBox) {
        comboBox.setFont(FONT_NORMAL);
        comboBox.setBackground(COLOR_PANEL);
        comboBox.setForeground(COLOR_TEXT);
        comboBox.setBorder(new LineBorder(COLOR_BORDER, 1, true));
    }

    private void styleButton(JButton button, boolean primary) {
        button.setFont(FONT_TITLE);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        if (primary) {
            button.setBackground(COLOR_ACCENT);
            button.setForeground(Color.WHITE);
        } else {
            button.setBackground(new Color(232, 239, 251));
            button.setForeground(COLOR_ACCENT_DARK);
        }
    }

    private void setCardBorder(JComponent component, String title) {
        component.setBorder(buildCardBorder(title));
    }

    private TitledBorder buildCardBorder(String title) {
        TitledBorder border = BorderFactory.createTitledBorder(
                new CompoundBorder(
                        new LineBorder(COLOR_BORDER, 1, true),
                        new EmptyBorder(6, 8, 8, 8)),
                title);
        border.setTitleColor(COLOR_TEXT);
        border.setTitleFont(FONT_TITLE);
        return border;
    }

    private void fillTableFromResponse(String responseText) {
        renderKeyValueFallback("raw", "");
        int idx = responseText.indexOf('\n');
        if (idx < 0 || idx + 1 >= responseText.length()) {
            toggleParsedResult(false);
            return;
        }
        String json = responseText.substring(idx + 1).trim();
        try {
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            JsonElement dataElement = extractDataElement(root);
            if (dataElement == null || dataElement.isJsonNull()) {
                renderKeyValueFallback("data", "");
                toggleParsedResult(false);
                return;
            }
            if (dataElement.isJsonObject()) {
                JsonObject dataObject = dataElement.getAsJsonObject();
                JsonArray arr = pickArray(dataObject);
                if (arr != null) {
                    renderObjectArray(arr);
                    toggleParsedResult(true);
                    return;
                }
                renderObjectArray(singletonArray(dataObject));
                toggleParsedResult(true);
                return;
            }
            if (dataElement.isJsonArray()) {
                JsonArray arr = dataElement.getAsJsonArray();
                if (!arr.isEmpty() && arr.get(0).isJsonObject()) {
                    renderObjectArray(arr);
                    toggleParsedResult(true);
                } else {
                    renderKeyValueFallback("data", GSON.toJson(arr));
                    toggleParsedResult(false);
                }
                return;
            }
            renderKeyValueFallback("data", dataElement.toString());
            toggleParsedResult(false);
        } catch (Exception ignore) {
            // Keep raw response in text area when parse fails.
            toggleParsedResult(false);
        }
    }

    private void toggleParsedResult(boolean visible) {
        if (parsedResultScroll == null || centerSplitPane == null) {
            return;
        }
        parsedResultScroll.setVisible(visible);
        centerSplitPane.setDividerSize(visible ? 8 : 0);
        centerSplitPane.setResizeWeight(visible ? 0.75 : 1.0);
        centerSplitPane.revalidate();
        centerSplitPane.repaint();
    }

    /**
     * Bridge nested wheel scrolling:
     * when inner scroll reaches top/bottom, continue scrolling outer panel.
     */
    private void installNestedScrollBridge(JScrollPane inner, JScrollPane outer) {
        if (inner == null || outer == null) {
            return;
        }
        MouseWheelListener listener = e -> {
            JScrollBar innerBar = inner.getVerticalScrollBar();
            if (innerBar == null) {
                scrollOuter(outer, e);
                return;
            }
            int rotation = e.getWheelRotation();
            int value = innerBar.getValue();
            int min = innerBar.getMinimum();
            int max = innerBar.getMaximum();
            int extent = innerBar.getModel().getExtent();
            boolean atTop = value <= min;
            boolean atBottom = value + extent >= max;
            boolean down = rotation > 0;
            if ((down && atBottom) || (!down && atTop)) {
                scrollOuter(outer, e);
            }
        };
        inner.addMouseWheelListener(listener);
        inner.getViewport().addMouseWheelListener(listener);
        if (inner.getViewport().getView() != null) {
            inner.getViewport().getView().addMouseWheelListener(listener);
        }
    }

    private void scrollOuter(JScrollPane outer, MouseWheelEvent e) {
        JScrollBar bar = outer.getVerticalScrollBar();
        if (bar == null) {
            return;
        }
        int unit = bar.getUnitIncrement(e.getWheelRotation());
        int delta = e.getWheelRotation() * Math.max(16, unit) * 2;
        int target = Math.max(bar.getMinimum(), Math.min(bar.getMaximum(), bar.getValue() + delta));
        bar.setValue(target);
        e.consume();
    }

    /**
     * A scrollable panel that always tracks viewport width to avoid horizontal clipping
     * when the outer scroll pane has horizontal scroll disabled.
     */
    private static class WidthTrackingPanel extends JPanel implements Scrollable {
        private WidthTrackingPanel(LayoutManager layout) {
            super(layout);
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension base = super.getPreferredSize();
            if (getParent() instanceof JViewport viewport) {
                // Fill remaining viewport height when content is shorter,
                // while still allowing vertical scroll when content is taller.
                int targetHeight = Math.max(base.height, viewport.getExtentSize().height);
                return new Dimension(base.width, targetHeight);
            }
            return base;
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL
                    ? Math.max(visibleRect.height - 32, 16)
                    : Math.max(visibleRect.width - 32, 16);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private JsonElement extractDataElement(JsonObject root) {
        if (root == null || !root.has("data") || root.get("data").isJsonNull()) {
            return null;
        }
        JsonElement data = root.get("data");
        if (data.isJsonPrimitive() && data.getAsJsonPrimitive().isString()) {
            String raw = data.getAsString();
            if (raw == null || raw.isBlank()) {
                return null;
            }
            try {
                return GSON.fromJson(raw, JsonElement.class);
            } catch (Exception ignored) {
                return null;
            }
        }
        return data;
    }

    private JsonArray pickArray(JsonObject data) {
        for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
            JsonElement value = entry.getValue();
            if (value != null && value.isJsonArray()) {
                JsonArray arr = value.getAsJsonArray();
                if (!arr.isEmpty() && arr.get(0).isJsonObject()) {
                    return arr;
                }
            }
        }
        return null;
    }

    private JsonArray singletonArray(JsonObject obj) {
        JsonArray arr = new JsonArray();
        arr.add(obj);
        return arr;
    }

    private void renderObjectArray(JsonArray arr) {
        List<String> columns = new ArrayList<>();
        for (JsonElement element : arr) {
            if (!element.isJsonObject()) {
                continue;
            }
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                if (!columns.contains(entry.getKey())) {
                    columns.add(entry.getKey());
                }
            }
        }
        tableModel.setDataVector(new Object[0][0], columns.toArray());
        for (JsonElement element : arr) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject obj = element.getAsJsonObject();
            Vector<Object> row = new Vector<>();
            for (String col : columns) {
                JsonElement v = obj.get(col);
                if (v == null || v.isJsonNull()) {
                    row.add("");
                } else if (v.isJsonPrimitive()) {
                    row.add(v.getAsJsonPrimitive().isString() ? v.getAsString() : v.getAsJsonPrimitive().toString());
                } else {
                    row.add(GSON.toJson(v));
                }
            }
            tableModel.addRow(row);
        }
    }

    private void renderKeyValueFallback(String key, String value) {
        tableModel.setDataVector(new Object[0][0], new Object[]{"key", "value"});
        Vector<Object> row = new Vector<>();
        row.add(key);
        row.add(value);
        tableModel.addRow(row);
    }

    private void putNumberOrString(JsonObject body, String key, String value) {
        try {
            body.addProperty(key, new BigDecimal(value));
        } catch (Exception e) {
            body.addProperty(key, value);
        }
    }

    private void putBooleanOrString(JsonObject body, String key, String value) {
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            body.addProperty(key, Boolean.parseBoolean(value));
            return;
        }
        body.addProperty(key, value);
    }

    private String normalizeApiKey(String input) {
        if (input == null) {
            return "";
        }
        String value = input.trim();
        if (value.isBlank()) {
            return "";
        }
        if (value.regionMatches(true, 0, "api-key ", 0, "api-key ".length())) {
            String key = value.substring("api-key ".length()).trim();
            return key.isEmpty() ? "" : "api-key " + key;
        }
        return "api-key " + value;
    }

    private String stripApiKeyPrefix(String input) {
        if (input == null) {
            return "";
        }
        String value = input.trim();
        if (value.regionMatches(true, 0, "api-key ", 0, "api-key ".length())) {
            return value.substring("api-key ".length()).trim();
        }
        return value;
    }

    private Map<String, String> collectCurrentParamValues() {
        Map<String, String> values = new LinkedHashMap<>();
        for (ParamFieldBinding binding : currentParamBindings) {
            values.put(binding.field.key, binding.input.getText() == null ? "" : binding.input.getText().trim());
        }
        return values;
    }

    private void applySavedParamValues(String endpointPath) {
        if (appConfig == null || appConfig.getEndpointParamConfig() == null || endpointPath == null) {
            return;
        }
        Map<String, String> values = appConfig.getEndpointParamConfig().get(endpointPath);
        if (values == null || values.isEmpty()) {
            return;
        }
        for (ParamFieldBinding binding : currentParamBindings) {
            if (values.containsKey(binding.field.key)) {
                binding.input.setText(values.get(binding.field.key));
            }
        }
    }

    private void applyAutoSign(String path, JsonObject body) {
        if (body == null) {
            return;
        }
        String signKey = signKeyField == null ? null : signKeyField.getText();
        if (signKey == null || signKey.isBlank()) {
            return;
        }

        JsonObject signPayload = new JsonObject();
        if ("/pakGoPay/api/server/v1/queryOrder".equals(path)) {
            copyIfPresent(body, signPayload, "merchantId", "merchantOrderNo", "orderType");
        } else if ("/pakGoPay/api/server/v1/balance".equals(path)) {
            copyIfPresent(body, signPayload, "merhcantId");
        } else if ("/pakGoPay/api/server/v1/createCollectionOrder".equals(path)) {
            copyIfPresent(body, signPayload,
                    "merchantId", "merchantOrderNo", "paymentNo", "amount",
                    "currency", "notificationUrl", "channelParams", "remark");
        } else if ("/pakGoPay/api/server/v1/createPayOutOrder".equals(path)) {
            copyIfPresent(body, signPayload,
                    "merchantId", "merchantOrderNo", "paymentNo", "amount",
                    "currency", "notificationUrl", "bankCode", "accountName",
                    "accountNo", "channelParams", "remark");
        } else {
            return;
        }
        body.addProperty("sign", signSystemHmacSha256Base64(signPayload, signKey));
    }

    private void copyIfPresent(JsonObject source, JsonObject target, String... keys) {
        for (String key : keys) {
            if (source.has(key) && !source.get(key).isJsonNull()) {
                target.add(key, source.get(key));
            }
        }
    }

    private String signSystemHmacSha256Base64(JsonObject params, String signKey) {
        if (params == null || signKey == null) {
            return null;
        }
        String stringA = params.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null && !entry.getValue().isJsonNull())
                .filter(entry -> {
                    JsonElement value = entry.getValue();
                    if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                        return !value.getAsString().isBlank();
                    }
                    return true;
                })
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entry -> entry.getKey() + "=" + toSignValue(entry.getValue()))
                .collect(Collectors.joining("&"));
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(signKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(keySpec);
            byte[] digest = mac.doFinal(stringA.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("sign failed", e);
        }
    }

    private String toSignValue(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return "";
        }
        if (value.isJsonPrimitive()) {
            return value.getAsJsonPrimitive().isString()
                    ? value.getAsString()
                    : value.getAsJsonPrimitive().toString();
        }
        return GSON.toJson(value);
    }

    private String getSelectedBaseUrl() {
        Object editorItem = baseUrlCombo.getEditor().getItem();
        String value = editorItem == null ? null : String.valueOf(editorItem).trim();
        if (value == null || value.isBlank()) {
            return "http://127.0.0.1:8080";
        }
        return value;
    }

    private void setSelectedBaseUrl(String baseUrl) {
        String value = (baseUrl == null || baseUrl.isBlank()) ? "http://127.0.0.1:8080" : baseUrl.trim();
        baseUrlCombo.setSelectedItem(value);
    }

    private static class ApiEndpoint {
        private final String name;
        private final String path;
        private final String comment;
        private final ParamField[] fields;

        private ApiEndpoint(String name, String path, String comment, ParamField[] fields) {
            this.name = name;
            this.path = path;
            this.comment = comment;
            this.fields = fields;
        }

        @Override
        public String toString() {
            return name + " (" + path + ")";
        }
    }

    private record ParamField(String key, String labelEn, String labelZh, String defaultValue, ParamType type) {
    }

    private record ParamFieldBinding(ParamField field, JTextField input) {
    }

    private enum ParamType {
        STRING,
        NUMBER,
        BOOLEAN
    }

    private enum Language {
        EN("English"),
        ZH("中文");

        private final String text;

        Language(String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }
}
