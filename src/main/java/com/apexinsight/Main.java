package com.apexinsight;

import com.apexinsight.data.Database;
import com.apexinsight.service.ApexInsightService;
import com.apexinsight.ui.LoginFrame;
import com.apexinsight.web.WebPortalServer;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            Database database = new Database();
            database.initialize();
            ApexInsightService service = new ApexInsightService(database);
            WebPortalServer web = new WebPortalServer(service);
            try {
                web.start();
                System.out.println("ApexInsight web portal: " + web.getUrl());
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        web.close();
                    } catch (Exception exception) {
                        System.err.println("The ApexInsight web server could not shut down cleanly: "
                                + exception.getMessage());
                    }
                }, "apexinsight-web-shutdown"));
            } catch (Exception webFailure) {
                System.err.println("ApexInsight desktop is ready, but the web portal could not start: "
                        + webFailure.getMessage());
            }
            SwingUtilities.invokeLater(() -> new LoginFrame(service).setVisible(true));
        } catch (Exception exception) {
            exception.printStackTrace();
            javax.swing.JOptionPane.showMessageDialog(null,
                    "ApexInsight could not start: " + exception.getMessage(),
                    "Startup error", javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }
}
