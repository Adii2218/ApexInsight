package com.apexinsight.ui;

import com.apexinsight.model.User;
import com.apexinsight.service.AppException;
import com.apexinsight.service.ApexInsightService;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

public final class LoginFrame extends JFrame {
    private final ApexInsightService service;
    private final JTextField username = new JTextField("manager", 22);
    private final JPasswordField password = new JPasswordField(22);
    private final javax.swing.JButton signIn = UiStyle.button("Sign in", true);

    public LoginFrame(ApexInsightService service) {
        this.service = service;
        setTitle("ApexInsight · Sign in");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(880, 640);
        setMinimumSize(new Dimension(780, 590));
        setLocationRelativeTo(null);
        UiStyle.install();

        JPanel shell = new JPanel(new GridBagLayout());
        shell.setBackground(UiStyle.LIGHT);
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createLineBorder(UiStyle.BORDER));
        card.setPreferredSize(new Dimension(750, 500));

        JPanel brand = new JPanel(new GridBagLayout());
        brand.setBackground(UiStyle.NAVY);
        brand.setPreferredSize(new Dimension(305, 500));
        JLabel mark = new JLabel("A");
        mark.setOpaque(true);
        mark.setBackground(UiStyle.BLUE);
        mark.setForeground(Color.WHITE);
        mark.setFont(mark.getFont().deriveFont(Font.BOLD, 22f));
        mark.setHorizontalAlignment(SwingConstants.CENTER);
        mark.setPreferredSize(new Dimension(48, 48));
        JLabel title = new JLabel("ApexInsight");
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 26f));
        JLabel intro = new JLabel("<html><div style='width:215px;color:#D0D9E8'>Better conversations.<br>Clearer goals.<br>Stronger teams.</div></html>");
        intro.setFont(intro.getFont().deriveFont(15f));
        JPanel brandContent = new JPanel();
        brandContent.setOpaque(false);
        brandContent.setLayout(new javax.swing.BoxLayout(brandContent, javax.swing.BoxLayout.Y_AXIS));
        brandContent.add(mark);
        brandContent.add(javax.swing.Box.createVerticalStrut(25));
        brandContent.add(title);
        brandContent.add(javax.swing.Box.createVerticalStrut(14));
        brandContent.add(intro);
        brand.add(brandContent);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Color.WHITE);
        form.setBorder(BorderFactory.createEmptyBorder(34, 42, 28, 42));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(5, 0, 5, 0);
        addFormRow(form, c, 0, "Welcome back", headerLabel("Sign in to your workspace"));
        addFormRow(form, c, 1, "Username", username);
        addFormRow(form, c, 2, "Password", password);
        signIn.addActionListener(event -> signIn());
        c.gridy = 6;
        c.insets = new Insets(18, 0, 8, 0);
        form.add(signIn, c);
        JLabel demo = new JLabel("<html><b>Demo accounts</b><br>Manager (Jordan Lee): manager / manager123<br>Aryan: aryan / aryan123<br>Shikha: shikha / shikha123<br>Tanvi: tanvi / tanvi123</html>");
        demo.setForeground(UiStyle.MUTED);
        demo.setFont(demo.getFont().deriveFont(12f));
        c.gridy = 7;
        c.insets = new Insets(12, 0, 0, 0);
        form.add(demo, c);
        password.addActionListener(event -> signIn());
        javax.swing.JButton web = UiStyle.button("Open the web portal", false);
        web.addActionListener(event -> openWebPortal());
        c.gridy = 8;
        c.insets = new Insets(8, 0, 0, 0);
        form.add(web, c);

        card.add(brand, BorderLayout.WEST);
        card.add(form, BorderLayout.CENTER);
        shell.add(card);
        setContentPane(shell);
        getRootPane().setDefaultButton(signIn);
    }

    private static JLabel headerLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(14f));
        label.setForeground(UiStyle.MUTED);
        return label;
    }

    private static void addFormRow(JPanel panel, GridBagConstraints c, int row, String label, java.awt.Component input) {
        JLabel caption = new JLabel(label);
        caption.setForeground(UiStyle.INK);
        caption.setFont(caption.getFont().deriveFont(Font.BOLD, row == 0 ? 22f : 13f));
        c.gridy = row == 0 ? 0 : row * 2;
        if (row == 0) {
            panel.add(caption, c);
            c.gridy = 1;
            c.insets = new Insets(0, 0, 18, 0);
            panel.add(input, c);
            c.insets = new Insets(5, 0, 5, 0);
            return;
        }
        panel.add(caption, c);
        c.gridy++;
        input.setFont(input.getFont().deriveFont(14f));
        if (input instanceof JTextField field) {
            field.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UiStyle.BORDER),
                    BorderFactory.createEmptyBorder(9, 10, 9, 10)));
        }
        panel.add(input, c);
    }

    private void signIn() {
        char[] secret = password.getPassword();
        String login = username.getText();
        signIn.setEnabled(false);
        signIn.setText("Signing in...");
        new SwingWorker<User, Void>() {
            @Override
            protected User doInBackground() throws AppException {
                return service.login(login, secret);
            }

            @Override
            protected void done() {
                try {
                    User user = get();
                    dispose();
                    new DashboardFrame(service, user).setVisible(true);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showError("Sign-in was interrupted. Please try again.");
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    showError(cause instanceof AppException ? cause.getMessage()
                            : "Sign-in failed. Please check the database and try again.");
                } finally {
                    Arrays.fill(secret, '\0');
                    password.setText("");
                    signIn.setEnabled(true);
                    signIn.setText("Sign in");
                }
            }
        }.execute();
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Sign-in failed", JOptionPane.WARNING_MESSAGE);
    }

    private void openWebPortal() {
        try {
            if (!java.awt.Desktop.isDesktopSupported()) {
                throw new java.io.IOException("This computer cannot open a browser automatically.");
            }
            java.awt.Desktop.getDesktop().browse(URI.create("http://127.0.0.1:"
                    + Integer.getInteger("apexinsight.web.port", 8085) + "/"));
        } catch (java.io.IOException | UnsupportedOperationException exception) {
            JOptionPane.showMessageDialog(this,
                    exception.getMessage() + "\nOpen http://127.0.0.1:"
                            + Integer.getInteger("apexinsight.web.port", 8085) + " in your browser.",
                    "Web portal", JOptionPane.WARNING_MESSAGE);
        }
    }
}
