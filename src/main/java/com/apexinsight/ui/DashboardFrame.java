package com.apexinsight.ui;

import com.apexinsight.model.Employee;
import com.apexinsight.model.Evaluation;
import com.apexinsight.model.Goal;
import com.apexinsight.model.Manager;
import com.apexinsight.model.ProgressTrackable;
import com.apexinsight.model.User;
import com.apexinsight.service.AppException;
import com.apexinsight.service.ApexInsightService;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.TableRowSorter;
import javax.swing.table.DefaultTableModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public final class DashboardFrame extends JFrame {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private final ApexInsightService service;
    private final User user;
    private final JLabel connectionStatus = new JLabel("Ready");
    private JTabbedPane tabs;

    public DashboardFrame(ApexInsightService service, User user) {
        this.service = service;
        this.user = user;
        setTitle("ApexInsight · " + user.getRoleLabel() + " workspace");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1000, 700));
        setSize(1230, 820);
        setLocationRelativeTo(null);
        build();
    }

    private void build() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UiStyle.LIGHT);
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBackground(UiStyle.NAVY);
        header.setBorder(BorderFactory.createEmptyBorder(14, 28, 14, 26));
        JLabel brand = new JLabel("APEXINSIGHT");
        brand.setForeground(Color.WHITE);
        brand.setFont(brand.getFont().deriveFont(Font.BOLD, 17f));
        JPanel identity = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 10, 0));
        identity.setOpaque(false);
        JLabel signedIn = new JLabel(user.getFullName() + "  ·  " + user.getRoleLabel());
        signedIn.setForeground(new Color(221, 229, 241));
        JButton changePassword = UiStyle.button("Password", false);
        changePassword.addActionListener(event -> changePassword());
        JButton logout = UiStyle.button("Sign out", false);
        logout.addActionListener(event -> {
            dispose();
            new LoginFrame(service).setVisible(true);
        });
        identity.add(signedIn);
        identity.add(changePassword);
        identity.add(logout);
        header.add(brand, BorderLayout.WEST);
        header.add(identity, BorderLayout.EAST);

        tabs = new JTabbedPane();
        tabs.setFont(tabs.getFont().deriveFont(Font.BOLD, 13f));
        tabs.addTab("Overview", new OverviewPanel());
        if (user instanceof Manager) {
            tabs.addTab("Performance reviews", new ManagerReviewsPanel());
            tabs.addTab("Goals", new ManagerGoalsPanel());
            tabs.addTab("Team", new PeoplePanel());
        } else {
            tabs.addTab("My reviews", new EmployeeReviewsPanel());
            tabs.addTab("My goals", new EmployeeGoalsPanel());
        }
        tabs.addChangeListener(event -> reloadSelectedPanel());

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(UiStyle.LIGHT);
        content.setBorder(BorderFactory.createEmptyBorder(19, 28, 17, 28));
        content.add(tabs);
        connectionStatus.setForeground(UiStyle.MUTED);
        connectionStatus.setBorder(BorderFactory.createEmptyBorder(0, 30, 10, 8));
        root.add(header, BorderLayout.NORTH);
        root.add(content, BorderLayout.CENTER);
        root.add(connectionStatus, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private void reloadSelectedPanel() {
        Component panel = tabs.getSelectedComponent();
        if (panel instanceof Refreshable refreshable) refreshable.refresh();
    }

    private void changePassword() {
        JPasswordField current = new JPasswordField(22);
        JPasswordField next = new JPasswordField(22);
        JPasswordField confirmation = new JPasswordField(22);
        JPanel fields = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        addField(fields, c, 0, "Current password", current);
        addField(fields, c, 1, "New password (8–128 characters)", next);
        addField(fields, c, 2, "Confirm new password", confirmation);
        if (JOptionPane.showConfirmDialog(this, fields, "Change password", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        char[] oldPassword = current.getPassword();
        char[] newPassword = next.getPassword();
        char[] confirmPassword = confirmation.getPassword();
        if (!java.util.Arrays.equals(newPassword, confirmPassword)) {
            java.util.Arrays.fill(oldPassword, '\0');
            java.util.Arrays.fill(newPassword, '\0');
            java.util.Arrays.fill(confirmPassword, '\0');
            JOptionPane.showMessageDialog(this, "The new passwords do not match.", "Password not changed",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        background("Changing password…", () -> {
            try {
                service.changeOwnPassword(user, oldPassword, newPassword);
                return null;
            } finally {
                java.util.Arrays.fill(oldPassword, '\0');
                java.util.Arrays.fill(newPassword, '\0');
                java.util.Arrays.fill(confirmPassword, '\0');
            }
        }, ignored -> JOptionPane.showMessageDialog(this, "Your password was changed.",
                "Password changed", JOptionPane.INFORMATION_MESSAGE));
    }

    private abstract class ContentPanel extends JPanel implements Refreshable {
        private final String panelTitle;
        private final JPanel body = new JPanel(new BorderLayout(0, 14));

        ContentPanel(String title, String subtitle) {
            super(new BorderLayout(0, 17));
            panelTitle = title;
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiStyle.BORDER),
                    BorderFactory.createEmptyBorder(22, 24, 23, 24)));
            JPanel intro = new JPanel(new BorderLayout(0, 5));
            intro.setOpaque(false);
            JLabel heading = new JLabel(title);
            heading.setFont(heading.getFont().deriveFont(Font.BOLD, 22f));
            heading.setForeground(UiStyle.INK);
            JLabel description = new JLabel(subtitle);
            description.setFont(description.getFont().deriveFont(13f));
            description.setForeground(UiStyle.MUTED);
            intro.add(heading, BorderLayout.NORTH);
            intro.add(description, BorderLayout.CENTER);
            body.setOpaque(false);
            add(intro, BorderLayout.NORTH);
            add(body, BorderLayout.CENTER);
        }

        JPanel body() {
            return body;
        }

        String panelTitle() {
            return panelTitle;
        }

        JPanel card() {
            JPanel card = new JPanel(new GridBagLayout());
            card.setBackground(UiStyle.LIGHT);
            card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiStyle.BORDER),
                    BorderFactory.createEmptyBorder(16, 17, 16, 17)));
            return card;
        }

        void field(JPanel panel, GridBagConstraints c, int row, String label, Component input) {
            addField(panel, c, row, label, input);
        }

        void error(AppException exception) {
            JOptionPane.showMessageDialog(DashboardFrame.this, exception.getMessage(), "Could not complete action",
                    JOptionPane.WARNING_MESSAGE);
        }

        JPanel listToolbar(JTable table, boolean allowExport) {
            JPanel toolbar = new JPanel(new BorderLayout(9, 0));
            toolbar.setOpaque(false);
            JTextField search = new JTextField(18);
            search.setToolTipText("Search this table");
            search.putClientProperty("JTextField.placeholderText", "Search this table…");
            JLabel searchLabel = new JLabel("Search");
            searchLabel.setForeground(UiStyle.MUTED);
            TableRowSorter<?> sorter = new TableRowSorter<>(table.getModel());
            table.setRowSorter(sorter);
            search.getDocument().addDocumentListener(new DocumentListener() {
                private void update() {
                    String text = search.getText().trim();
                    sorter.setRowFilter(text.isEmpty() ? null
                            : RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text)));
                }

                @Override public void insertUpdate(DocumentEvent event) { update(); }
                @Override public void removeUpdate(DocumentEvent event) { update(); }
                @Override public void changedUpdate(DocumentEvent event) { update(); }
            });
            JPanel left = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
            left.setOpaque(false);
            left.add(searchLabel);
            left.add(search);
            toolbar.add(left, BorderLayout.WEST);
            if (allowExport) {
                JButton export = UiStyle.button("Export CSV", false);
                export.addActionListener(event -> exportTable(table));
                toolbar.add(export, BorderLayout.EAST);
            }
            return toolbar;
        }

        void setTableRows(DefaultTableModel model, JTable table, Object[][] rows) {
            int selected = table.getSelectedRow();
            model.setRowCount(0);
            for (Object[] row : rows) model.addRow(row);
            if (selected >= 0 && selected < table.getRowCount()) table.setRowSelectionInterval(selected, selected);
        }
    }

    private final class OverviewPanel extends ContentPanel {
        private final JPanel metrics = new JPanel(new java.awt.GridLayout(1, 3, 14, 0));
        private final DefaultTableModel model = model("Date", "Activity", "Details");
        private final JTable recent = new JTable(model);

        OverviewPanel() {
            super("Welcome, " + user.getFullName(), user instanceof Manager
                    ? "A live snapshot of your team and recent activity."
                    : "A personal snapshot of your feedback and growth goals.");
            metrics.setOpaque(false);
            JPanel upper = new JPanel(new BorderLayout(0, 15));
            upper.setOpaque(false);
            upper.add(metrics, BorderLayout.NORTH);
            JPanel activity = new JPanel(new BorderLayout(0, 10));
            activity.setOpaque(false);
            activity.add(sectionTitle("Recent activity"), BorderLayout.NORTH);
            UiStyle.styleTable(recent);
            recent.setAutoCreateRowSorter(true);
            activity.add(new JScrollPane(recent), BorderLayout.CENTER);
            upper.add(activity, BorderLayout.CENTER);
            JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
            actions.setOpaque(false);
            JButton refresh = UiStyle.button("Refresh overview", false);
            refresh.addActionListener(event -> refresh());
            actions.add(refresh);
            body().add(upper, BorderLayout.CENTER);
            body().add(actions, BorderLayout.SOUTH);
            refresh();
        }

        @Override
        public void refresh() {
            background("Loading overview…", () -> {
                List<Evaluation> reviews = user instanceof Manager
                        ? service.getAllEvaluations(user) : service.getEvaluations(user, user.getId());
                List<Goal> goals = service.getGoals(user);
                List<Employee> employees = user instanceof Manager ? service.getEmployees(user) : List.of();
                return new Object[]{reviews, goals, employees};
            }, result -> {
                @SuppressWarnings("unchecked") List<Evaluation> reviews = (List<Evaluation>) result[0];
                @SuppressWarnings("unchecked") List<Goal> goals = (List<Goal>) result[1];
                @SuppressWarnings("unchecked") List<Employee> employees = (List<Employee>) result[2];
                double average = reviews.stream().mapToInt(Evaluation::rating).average().orElse(0);
                long inProgress = goals.stream().filter(goal -> goal.progress() < 100).count();
                int goalProgress = (int) Math.round(goals.stream()
                        .mapToInt(ProgressTrackable::progress).average().orElse(0));
                if (user instanceof Manager) {
                    showMetrics(new String[]{"Team members", "Performance reviews", "Active goals"},
                            new String[]{String.valueOf(employees.size()), String.valueOf(reviews.size()),
                                    inProgress + " · " + goalProgress + "% avg"});
                } else {
                    showMetrics(new String[]{"Average review", "Reviews received", "Goals completed"},
                            new String[]{reviews.isEmpty() ? "—" : String.format(java.util.Locale.ROOT, "%.1f / 5", average),
                                    String.valueOf(reviews.size()),
                                    goals.stream().filter(goal -> goal.progress() == 100).count()
                                            + " of " + goals.size()});
                }
                List<java.util.Map.Entry<LocalDate, Object[]>> rows = new ArrayList<>();
                for (Evaluation evaluation : reviews.stream().limit(8).toList()) {
                    rows.add(java.util.Map.entry(evaluation.createdAt().toLocalDate(), new Object[]{
                            DATE_FORMAT.format(evaluation.createdAt()), "Performance review · " + evaluation.rating() + " / 5",
                            user instanceof Manager ? evaluation.employeeName() : evaluation.managerName()}));
                }
                for (Goal goal : goals.stream().limit(8).toList()) {
                    rows.add(java.util.Map.entry(goal.dueDate(), new Object[]{DATE_FORMAT.format(goal.dueDate()),
                            "Goal · " + goal.progress() + "%",
                            (user instanceof Manager ? goal.employeeName() + " · " : "") + goal.title()}));
                }
                rows.sort(java.util.Map.Entry.<LocalDate, Object[]>comparingByKey().reversed());
                model.setRowCount(0);
                rows.stream().limit(10).map(java.util.Map.Entry::getValue).forEach(model::addRow);
            });
        }

        private void showMetrics(String[] titles, String[] values) {
            metrics.removeAll();
            for (int i = 0; i < titles.length; i++) {
                JPanel metric = new JPanel(new BorderLayout(0, 7));
                metric.setBackground(UiStyle.LIGHT);
                metric.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiStyle.BORDER),
                        BorderFactory.createEmptyBorder(15, 17, 15, 17)));
                JLabel caption = new JLabel(titles[i]);
                caption.setForeground(UiStyle.MUTED);
                caption.setFont(caption.getFont().deriveFont(Font.BOLD, 12f));
                JLabel value = new JLabel(values[i]);
                value.setForeground(UiStyle.NAVY);
                value.setFont(value.getFont().deriveFont(Font.BOLD, 25f));
                metric.add(caption, BorderLayout.NORTH);
                metric.add(value, BorderLayout.CENTER);
                metrics.add(metric);
            }
            metrics.revalidate();
            metrics.repaint();
        }
    }

    private final class ManagerReviewsPanel extends ContentPanel {
        private final JComboBox<Employee> employeeBox = employeeCombo();
        private final JComboBox<Integer> ratingBox = new JComboBox<>(new Integer[]{5, 4, 3, 2, 1});
        private final JTextArea feedback = textArea(6);
        private final DefaultTableModel model = model("ID", "Date", "Employee", "Rating", "Feedback");
        private final JTable table = new JTable(model);
        private List<Evaluation> entries = List.of();
        private int selectedId;
        private boolean loadingSelection;

        ManagerReviewsPanel() {
            super("Performance reviews", "Write, edit, search, export, and remove employee reviews.");
            JPanel editor = card();
            GridBagConstraints c = gridConstraints();
            field(editor, c, 0, "Team member", employeeBox);
            field(editor, c, 1, "Performance rating (1–5)", ratingBox);
            field(editor, c, 2, "Detailed feedback", new JScrollPane(feedback));
            JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 7, 0));
            actions.setOpaque(false);
            JButton save = UiStyle.button("Save review", true);
            save.addActionListener(event -> saveReview());
            JButton clear = UiStyle.button("New review", false);
            clear.addActionListener(event -> clearEditor());
            JButton delete = UiStyle.button("Delete", false);
            delete.addActionListener(event -> deleteReview());
            actions.add(save);
            actions.add(clear);
            actions.add(delete);
            c.gridy = 6;
            c.insets = new Insets(14, 0, 0, 0);
            editor.add(actions, c);
            table.getColumnModel().getColumn(0).setMinWidth(0);
            table.getColumnModel().getColumn(0).setMaxWidth(0);
            table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
            table.getColumnModel().getColumn(3).setPreferredWidth(75);
            table.getColumnModel().getColumn(4).setPreferredWidth(340);
            UiStyle.styleTable(table);
            employeeBox.addActionListener(event -> refresh());
            table.getSelectionModel().addListSelectionListener(this::selectReview);
            JPanel listing = new JPanel(new BorderLayout(0, 9));
            listing.setOpaque(false);
            listing.add(listToolbar(table, true), BorderLayout.NORTH);
            listing.add(new JScrollPane(table), BorderLayout.CENTER);
            addTwoColumnSplit(body(), editor, listing);
            loadEmployees();
            refresh();
        }

        private void loadEmployees() {
            background("Loading team…", () -> service.getEmployees(user),
                    people -> employeeBox.setModel(new DefaultComboBoxModel<>(people.toArray(Employee[]::new))));
        }

        @Override
        public void refresh() {
            if (employeeBox.getItemCount() == 0) return;
            background("Loading reviews…", () -> service.getAllEvaluations(user), all -> {
                entries = all;
                List<Evaluation> selected = selectedEmployee() == null ? all
                        : all.stream().filter(review -> review.employeeId() == selectedEmployee().getId()).toList();
                model.setRowCount(0);
                for (Evaluation evaluation : selected) {
                    model.addRow(new Object[]{evaluation.id(), DATE_FORMAT.format(evaluation.createdAt()),
                            evaluation.employeeName(), evaluation.rating() + " / 5", evaluation.feedback()});
                }
            });
        }

        private Employee selectedEmployee() {
            return (Employee) employeeBox.getSelectedItem();
        }

        private void selectReview(ListSelectionEvent event) {
            if (event.getValueIsAdjusting() || loadingSelection || table.getSelectedRow() < 0) return;
            int row = table.convertRowIndexToModel(table.getSelectedRow());
            int id = ((Number) model.getValueAt(row, 0)).intValue();
            Evaluation evaluation = entries.stream().filter(candidate -> candidate.id() == id).findFirst().orElse(null);
            if (evaluation == null) return;
            selectedId = evaluation.id();
            loadingSelection = true;
            for (int i = 0; i < employeeBox.getItemCount(); i++) {
                if (employeeBox.getItemAt(i).getId() == evaluation.employeeId()) {
                    employeeBox.setSelectedIndex(i);
                    break;
                }
            }
            ratingBox.setSelectedItem(evaluation.rating());
            feedback.setText(evaluation.feedback());
            loadingSelection = false;
        }

        private void saveReview() {
            Employee employee = selectedEmployee();
            if (employee == null) return;
            int id = selectedId;
            int rating = (Integer) ratingBox.getSelectedItem();
            String text = feedback.getText();
            background(id == 0 ? "Saving review…" : "Updating review…", () -> {
                if (id == 0) service.addEvaluation(user, employee.getId(), rating, text);
                else service.updateEvaluation(user, id, employee.getId(), rating, text);
                return null;
            }, ignored -> {
                clearEditor();
                refresh();
            });
        }

        private void deleteReview() {
            if (selectedId == 0) {
                JOptionPane.showMessageDialog(this, "Select a review first.", "No review selected",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            if (!confirm("Delete this performance review? Employees will no longer see it.")) return;
            int id = selectedId;
            background("Deleting review…", () -> {
                service.deleteEvaluation(user, id);
                return null;
            }, ignored -> {
                clearEditor();
                refresh();
            });
        }

        private void clearEditor() {
            selectedId = 0;
            feedback.setText("");
            ratingBox.setSelectedIndex(0);
            table.clearSelection();
        }
    }

    private final class ManagerGoalsPanel extends ContentPanel {
        private final JComboBox<Employee> employeeBox = employeeCombo();
        private final JTextField title = new JTextField();
        private final JTextArea description = textArea(3);
        private final JTextField dueDate = new JTextField(LocalDate.now().plusMonths(1).toString());
        private final JComboBox<String> status = new JComboBox<>(ApexInsightService.goalStatuses().toArray(String[]::new));
        private final JSpinner progress = new JSpinner(new SpinnerNumberModel(0, 0, 100, 5));
        private final DefaultTableModel model = model("ID", "Employee", "Goal", "Due date", "Status", "Progress");
        private final JTable table = new JTable(model);
        private List<Goal> entries = List.of();
        private int selectedId;

        ManagerGoalsPanel() {
            super("Goals & progress", "Create assignments, track progress, and update team goals.");
            JPanel editor = card();
            GridBagConstraints c = gridConstraints();
            field(editor, c, 0, "Team member", employeeBox);
            field(editor, c, 1, "Goal title", title);
            field(editor, c, 2, "Description", new JScrollPane(description));
            field(editor, c, 3, "Due date (YYYY-MM-DD)", dueDate);
            field(editor, c, 4, "Status", status);
            field(editor, c, 5, "Progress (%)", progress);
            JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 7, 0));
            actions.setOpaque(false);
            JButton save = UiStyle.button("Save goal", true);
            save.addActionListener(event -> saveGoal());
            JButton clear = UiStyle.button("New goal", false);
            clear.addActionListener(event -> clearEditor());
            JButton delete = UiStyle.button("Delete", false);
            delete.addActionListener(event -> deleteGoal());
            actions.add(save);
            actions.add(clear);
            actions.add(delete);
            c.gridy = 12;
            c.insets = new Insets(14, 0, 0, 0);
            editor.add(actions, c);
            hideIdColumn(table);
            table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
            table.getSelectionModel().addListSelectionListener(this::selectGoal);
            employeeBox.addActionListener(event -> refresh());
            JPanel listing = new JPanel(new BorderLayout(0, 9));
            listing.setOpaque(false);
            listing.add(listToolbar(table, true), BorderLayout.NORTH);
            listing.add(new JScrollPane(table), BorderLayout.CENTER);
            addTwoColumnSplit(body(), editor, listing);
            loadEmployees();
            refresh();
        }

        private void loadEmployees() {
            background("Loading team…", () -> service.getEmployees(user),
                    people -> employeeBox.setModel(new DefaultComboBoxModel<>(people.toArray(Employee[]::new))));
        }

        @Override
        public void refresh() {
            if (employeeBox.getItemCount() == 0) return;
            background("Loading goals…", () -> service.getGoals(user), goals -> {
                entries = goals;
                Employee selected = (Employee) employeeBox.getSelectedItem();
                model.setRowCount(0);
                for (Goal goal : goals) {
                    if (selected != null && goal.employeeId() == selected.getId()) {
                        model.addRow(new Object[]{goal.id(), goal.employeeName(), goal.title(),
                                DATE_FORMAT.format(goal.dueDate()), goal.status(), goal.progress() + "%"});
                    }
                }
            });
        }

        private void selectGoal(ListSelectionEvent event) {
            if (event.getValueIsAdjusting() || table.getSelectedRow() < 0) return;
            int row = table.convertRowIndexToModel(table.getSelectedRow());
            int id = ((Number) model.getValueAt(row, 0)).intValue();
            Goal goal = entries.stream().filter(candidate -> candidate.id() == id).findFirst().orElse(null);
            if (goal == null) return;
            selectedId = goal.id();
            title.setText(goal.title());
            description.setText(goal.description());
            dueDate.setText(goal.dueDate().toString());
            status.setSelectedItem(goal.status());
            progress.setValue(goal.progress());
            selectEmployee(employeeBox, goal.employeeId());
        }

        private void saveGoal() {
            Employee employee = (Employee) employeeBox.getSelectedItem();
            if (employee == null) return;
            final LocalDate parsedDate;
            try {
                parsedDate = LocalDate.parse(dueDate.getText().trim());
            } catch (DateTimeParseException exception) {
                JOptionPane.showMessageDialog(this, "Enter a valid due date in YYYY-MM-DD format.",
                        "Check due date", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int id = selectedId;
            String goalTitle = title.getText();
            String goalDescription = description.getText();
            String goalStatus = (String) status.getSelectedItem();
            int goalProgress = (Integer) progress.getValue();
            background(id == 0 ? "Saving goal…" : "Updating goal…", () -> {
                service.saveGoal(user, id, employee.getId(), goalTitle, goalDescription, parsedDate,
                        goalStatus, goalProgress);
                return null;
            }, ignored -> {
                clearEditor();
                refresh();
            });
        }

        private void deleteGoal() {
            if (selectedId == 0) {
                JOptionPane.showMessageDialog(this, "Select a goal first.", "No goal selected",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            if (!confirm("Delete this goal? The employee will no longer see it.")) return;
            int id = selectedId;
            background("Deleting goal…", () -> {
                service.deleteGoal(user, id);
                return null;
            }, ignored -> {
                clearEditor();
                refresh();
            });
        }

        private void clearEditor() {
            selectedId = 0;
            title.setText("");
            description.setText("");
            dueDate.setText(LocalDate.now().plusMonths(1).toString());
            status.setSelectedItem("Not started");
            progress.setValue(0);
            table.clearSelection();
        }
    }

    private final class PeoplePanel extends ContentPanel {
        private final JTextField username = new JTextField();
        private final JTextField fullName = new JTextField();
        private final JTextField department = new JTextField();
        private final JTextField jobTitle = new JTextField();
        private final JPasswordField initialPassword = new JPasswordField();
        private final DefaultTableModel model = model("ID", "Name", "Username", "Department", "Job title");
        private final JTable table = new JTable(model);
        private List<Employee> employees = List.of();
        private int selectedId;

        PeoplePanel() {
            super("Team accounts", "Add, update, reset passwords, and remove employee accounts.");
            JPanel editor = card();
            GridBagConstraints c = gridConstraints();
            field(editor, c, 0, "Username", username);
            field(editor, c, 1, "Full name", fullName);
            field(editor, c, 2, "Department", department);
            field(editor, c, 3, "Job title", jobTitle);
            field(editor, c, 4, "Initial password (new account only)", initialPassword);
            JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 7, 0));
            actions.setOpaque(false);
            JButton save = UiStyle.button("Save employee", true);
            save.addActionListener(event -> saveEmployee());
            JButton clear = UiStyle.button("New employee", false);
            clear.addActionListener(event -> clearEditor());
            JButton reset = UiStyle.button("Reset password", false);
            reset.addActionListener(event -> resetPassword());
            JButton delete = UiStyle.button("Delete account", false);
            delete.addActionListener(event -> deleteEmployee());
            actions.add(save);
            actions.add(clear);
            actions.add(reset);
            actions.add(delete);
            c.gridy = 10;
            c.insets = new Insets(14, 0, 0, 0);
            editor.add(actions, c);
            hideIdColumn(table);
            table.getSelectionModel().addListSelectionListener(this::selectEmployee);
            JPanel listing = new JPanel(new BorderLayout(0, 9));
            listing.setOpaque(false);
            listing.add(listToolbar(table, true), BorderLayout.NORTH);
            listing.add(new JScrollPane(table), BorderLayout.CENTER);
            addTwoColumnSplit(body(), editor, listing);
            refresh();
        }

        @Override
        public void refresh() {
            background("Loading team accounts…", () -> service.getEmployees(user), result -> {
                employees = result;
                model.setRowCount(0);
                for (Employee employee : employees) {
                    model.addRow(new Object[]{employee.getId(), employee.getFullName(),
                            employee.getUsername(), employee.getDepartment(), employee.getJobTitle()});
                }
            });
        }

        private void selectEmployee(ListSelectionEvent event) {
            if (event.getValueIsAdjusting() || table.getSelectedRow() < 0) return;
            int row = table.convertRowIndexToModel(table.getSelectedRow());
            int id = ((Number) model.getValueAt(row, 0)).intValue();
            Employee employee = employees.stream().filter(candidate -> candidate.getId() == id)
                    .findFirst().orElse(null);
            if (employee == null) return;
            selectedId = employee.getId();
            username.setText(employee.getUsername());
            fullName.setText(employee.getFullName());
            department.setText(employee.getDepartment());
            jobTitle.setText(employee.getJobTitle());
            initialPassword.setText("");
        }

        private void saveEmployee() {
            String login = username.getText();
            String name = fullName.getText();
            String team = department.getText();
            String titleText = jobTitle.getText();
            char[] password = initialPassword.getPassword();
            int id = selectedId;
            background(id == 0 ? "Creating employee…" : "Updating employee…", () -> {
                try {
                    if (id == 0) {
                        service.createEmployee(user, login, name, team, titleText, password);
                    } else {
                        service.updateEmployee(user, id, login, name, team, titleText);
                    }
                    return null;
                } finally {
                    java.util.Arrays.fill(password, '\0');
                }
            }, ignored -> {
                clearEditor();
                refresh();
            });
        }

        private void resetPassword() {
            if (selectedId == 0) {
                JOptionPane.showMessageDialog(this, "Select an employee first.", "No employee selected",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            JPasswordField password = new JPasswordField(20);
            if (JOptionPane.showConfirmDialog(this, password, "Set a new password (8–128 characters)",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
            char[] secret = password.getPassword();
            int id = selectedId;
            background("Resetting password…", () -> {
                try {
                    service.resetEmployeePassword(user, id, secret);
                    return null;
                } finally {
                    java.util.Arrays.fill(secret, '\0');
                }
            }, ignored -> JOptionPane.showMessageDialog(this, "The employee's password has been reset.",
                    "Password reset", JOptionPane.INFORMATION_MESSAGE));
        }

        private void deleteEmployee() {
            if (selectedId == 0) {
                JOptionPane.showMessageDialog(this, "Select an employee first.", "No employee selected",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            Employee employee = employees.stream().filter(candidate -> candidate.getId() == selectedId)
                    .findFirst().orElse(null);
            if (employee == null || !confirm("Delete " + employee.getFullName()
                    + " and permanently remove their reviews and goals?")) return;
            int id = selectedId;
            background("Deleting employee…", () -> {
                service.deleteEmployee(user, id);
                return null;
            }, ignored -> {
                clearEditor();
                refresh();
            });
        }

        private void clearEditor() {
            selectedId = 0;
            username.setText("");
            fullName.setText("");
            department.setText("");
            jobTitle.setText("");
            initialPassword.setText("");
            table.clearSelection();
        }
    }

    private final class EmployeeReviewsPanel extends ContentPanel {
        private final DefaultTableModel model = model("Date", "Rating", "Manager", "Feedback");
        private final JTable table = new JTable(model);

        EmployeeReviewsPanel() {
            super("Your performance", "Read your personal review history. Double-click feedback to read in full.");
            table.setAutoCreateRowSorter(true);
            table.setRowHeight(46);
            table.getColumnModel().getColumn(3).setPreferredWidth(450);
            table.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent event) {
                    if (event.getClickCount() == 2) showFeedback(table, 3);
                }
            });
            JPanel listing = new JPanel(new BorderLayout(0, 9));
            listing.setOpaque(false);
            listing.add(listToolbar(table, true), BorderLayout.NORTH);
            listing.add(new JScrollPane(table), BorderLayout.CENTER);
            body().add(listing);
            refresh();
        }

        @Override
        public void refresh() {
            background("Loading your reviews…", () -> service.getEvaluations(user, user.getId()), reviews -> {
                model.setRowCount(0);
                for (Evaluation review : reviews) {
                    model.addRow(new Object[]{DATE_FORMAT.format(review.createdAt()),
                            review.rating() + " / 5", review.managerName(), review.feedback()});
                }
            });
        }
    }

    private final class EmployeeGoalsPanel extends ContentPanel {
        private final DefaultTableModel model = model("ID", "Goal", "Description", "Due date", "Status", "Progress");
        private final JTable table = new JTable(model);
        private final JSpinner progress = new JSpinner(new SpinnerNumberModel(0, 0, 100, 5));
        private List<Goal> entries = List.of();
        private int selectedId;

        EmployeeGoalsPanel() {
            super("Your goals", "Update the progress on goals assigned to you and track each upcoming milestone.");
            hideIdColumn(table);
            table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
            table.getColumnModel().getColumn(2).setPreferredWidth(330);
            table.getSelectionModel().addListSelectionListener(this::selectGoal);
            JPanel toolbar = new JPanel(new BorderLayout(10, 0));
            toolbar.setOpaque(false);
            toolbar.add(listToolbar(table, true), BorderLayout.CENTER);
            JPanel update = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
            update.setOpaque(false);
            JLabel label = new JLabel("Your progress");
            label.setForeground(UiStyle.MUTED);
            JButton save = UiStyle.button("Update progress", true);
            save.addActionListener(event -> updateProgress());
            update.add(label);
            update.add(progress);
            update.add(save);
            toolbar.add(update, BorderLayout.SOUTH);
            body().add(toolbar, BorderLayout.NORTH);
            body().add(new JScrollPane(table), BorderLayout.CENTER);
            refresh();
        }

        @Override
        public void refresh() {
            background("Loading your goals…", () -> service.getGoals(user), result -> {
                entries = result;
                model.setRowCount(0);
                for (Goal goal : entries) {
                    model.addRow(new Object[]{goal.id(), goal.title(), goal.description(),
                            DATE_FORMAT.format(goal.dueDate()), goal.status(), goal.progress() + "%"});
                }
            });
        }

        private void selectGoal(ListSelectionEvent event) {
            if (event.getValueIsAdjusting() || table.getSelectedRow() < 0) return;
            int row = table.convertRowIndexToModel(table.getSelectedRow());
            int id = ((Number) model.getValueAt(row, 0)).intValue();
            Goal goal = entries.stream().filter(candidate -> candidate.id() == id).findFirst().orElse(null);
            if (goal == null) return;
            selectedId = goal.id();
            progress.setValue(goal.progress());
        }

        private void updateProgress() {
            if (selectedId == 0) {
                JOptionPane.showMessageDialog(this, "Select one of your goals first.", "No goal selected",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            int id = selectedId;
            int value = (Integer) progress.getValue();
            background("Saving goal progress…", () -> {
                service.updateGoalProgress(user, id, value);
                return null;
            }, ignored -> refresh());
        }
    }

    private static JComboBox<Employee> employeeCombo() {
        JComboBox<Employee> combo = new JComboBox<>();
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                                                           boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof Employee employee) {
                    setText(employee.getFullName() + " · " + employee.getJobTitle());
                }
                return this;
            }
        });
        return combo;
    }

    private <T> void background(String activity, Task<T> task, Consumer<T> success) {
        connectionStatus.setText(activity);
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return task.run();
            }

            @Override
            protected void done() {
                connectionStatus.setText("Ready");
                try {
                    success.accept(get());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showTaskError("This operation was interrupted. Please try again.");
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    showTaskError(cause instanceof AppException ? cause.getMessage()
                            : cause.getMessage() == null ? "Could not complete this operation."
                            : "Could not complete this operation: " + cause.getMessage());
                }
            }
        }.execute();
    }

    private void showTaskError(String message) {
        connectionStatus.setText("Action failed");
        JOptionPane.showMessageDialog(this, message, "Could not complete action", JOptionPane.WARNING_MESSAGE);
    }

    private void addTwoColumnSplit(JPanel target, JPanel editor, JPanel listing) {
        JPanel split = new JPanel(new java.awt.GridLayout(1, 2, 18, 0));
        split.setOpaque(false);
        split.add(editor);
        split.add(listing);
        target.add(split, BorderLayout.CENTER);
    }

    private void exportTable(JTable table) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export " + tabs.getTitleAt(tabs.getSelectedIndex()) + " as CSV");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV files (*.csv)", "csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        java.nio.file.Path file = chooser.getSelectedFile().toPath();
        if (!file.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".csv")) {
            file = java.nio.file.Path.of(file + ".csv");
        }
        if (Files.exists(file) && !confirm("Replace the existing file " + file.getFileName() + "?")) return;
        java.nio.file.Path destination = file;
        String csv = tableAsCsv(table);
        background("Exporting CSV…", () -> {
            Files.writeString(destination, csv, StandardCharsets.UTF_8);
            return null;
        }, ignored -> JOptionPane.showMessageDialog(this, "Exported " + destination.getFileName()
                + " successfully.", "Export complete", JOptionPane.INFORMATION_MESSAGE));
    }

    private static String tableAsCsv(JTable table) {
        StringBuilder csv = new StringBuilder();
        for (int column = 0; column < table.getColumnCount(); column++) {
            if (column > 0) csv.append(',');
            csv.append(csvValue(table.getColumnName(column)));
        }
        csv.append("\r\n");
        for (int row = 0; row < table.getRowCount(); row++) {
            for (int column = 0; column < table.getColumnCount(); column++) {
                if (column > 0) csv.append(',');
                Object value = table.getValueAt(row, column);
                csv.append(csvValue(value == null ? "" : value.toString()));
            }
            csv.append("\r\n");
        }
        return csv.toString();
    }

    private static String csvValue(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static void addField(JPanel panel, GridBagConstraints c, int row, String label, Component input) {
        JLabel caption = new JLabel(label);
        caption.setFont(caption.getFont().deriveFont(Font.BOLD, 12f));
        caption.setForeground(UiStyle.MUTED);
        c.gridx = 0;
        c.gridy = row * 2;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(row == 0 ? 0 : 12, 0, 5, 0);
        panel.add(caption, c);
        c.gridy++;
        c.insets = new Insets(0, 0, 0, 0);
        input.setFont(input.getFont().deriveFont(13f));
        if (input instanceof JTextField field) {
            field.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiStyle.BORDER),
                    BorderFactory.createEmptyBorder(8, 9, 8, 9)));
        }
        panel.add(input, c);
    }

    private static GridBagConstraints gridConstraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        return c;
    }

    private static JTextArea textArea(int rows) {
        JTextArea area = new JTextArea(rows, 23);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiStyle.BORDER),
                BorderFactory.createEmptyBorder(8, 9, 8, 9)));
        return area;
    }

    private static DefaultTableModel model(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
    }

    private static void hideIdColumn(JTable table) {
        table.getColumnModel().getColumn(0).setMinWidth(0);
        table.getColumnModel().getColumn(0).setMaxWidth(0);
    }

    private static JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 15f));
        label.setForeground(UiStyle.INK);
        return label;
    }

    private static void selectEmployee(JComboBox<Employee> employees, int employeeId) {
        for (int index = 0; index < employees.getItemCount(); index++) {
            if (employees.getItemAt(index).getId() == employeeId) {
                employees.setSelectedIndex(index);
                return;
            }
        }
    }

    private boolean confirm(String message) {
        return JOptionPane.showConfirmDialog(this, message, "Please confirm", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    private static void showFeedback(JTable table, int column) {
        if (table.getSelectedRow() < 0) return;
        int row = table.convertRowIndexToModel(table.getSelectedRow());
        Object feedback = table.getModel().getValueAt(row, column);
        JOptionPane.showMessageDialog(table, feedback, "Feedback details", JOptionPane.INFORMATION_MESSAGE);
    }

    @FunctionalInterface
    private interface Task<T> {
        T run() throws Exception;
    }

    private interface Refreshable {
        void refresh();
    }
}
