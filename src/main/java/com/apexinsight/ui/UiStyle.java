package com.apexinsight.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;
import java.awt.Font;

final class UiStyle {
    static final Color NAVY = new Color(20, 36, 64);
    static final Color BLUE = new Color(43, 103, 220);
    static final Color LIGHT = new Color(245, 247, 251);
    static final Color MUTED = new Color(101, 115, 136);
    static final Color BORDER = new Color(222, 228, 237);
    static final Color INK = new Color(35, 47, 66);

    private UiStyle() {
    }

    static void install() {
        UIManager.put("TabbedPane.selected", Color.WHITE);
        UIManager.put("TabbedPane.contentAreaColor", Color.WHITE);
        UIManager.put("TabbedPane.focus", new Color(0, 0, 0, 0));
        UIManager.put("ComboBox.focus", BLUE);
        UIManager.put("Component.focusColor", BLUE);
    }

    static JButton button(String text, boolean primary) {
        JButton button = new JButton(text);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 13f));
        button.setForeground(primary ? Color.WHITE : INK);
        button.setBackground(primary ? BLUE : Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(primary ? BLUE : BORDER),
                BorderFactory.createEmptyBorder(9, 16, 9, 16)));
        return button;
    }

    static void styleTable(JTable table) {
        table.setRowHeight(36);
        table.setFillsViewportHeight(true);
        table.setShowVerticalLines(false);
        table.setGridColor(BORDER);
        table.setSelectionBackground(new Color(229, 238, 255));
        table.setSelectionForeground(INK);
        table.setFont(table.getFont().deriveFont(13f));
        table.getTableHeader().setFont(table.getFont().deriveFont(Font.BOLD, 12f));
        table.getTableHeader().setBackground(LIGHT);
        table.getTableHeader().setForeground(MUTED);
        table.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        DefaultTableCellRenderer left = new DefaultTableCellRenderer();
        left.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        for (int index = 0; index < table.getColumnCount(); index++) {
            table.getColumnModel().getColumn(index).setCellRenderer(left);
        }
    }
}
