import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.stream.Stream;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class SmartFileOrganizer extends JFrame {
    private JTextArea logArea;
    private JProgressBar progressBar;
    private JButton selectFolderButton;
    private JLabel currentDirLabel;
    private Font emojiFont;
    private Font regularFont;

    public SmartFileOrganizer() {
        setTitle("📁 Smart File Organizer");
        setSize(600, 400);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));
        setResizable(false);

        String[] emojiFontCandidates = {"Segoe UI Emoji", "Apple Color Emoji", "Noto Color Emoji"};
        for (String fontName : emojiFontCandidates) {
            Font testFont = new Font(fontName, Font.PLAIN, 22);
            if (testFont.canDisplay('\uD83D') && testFont.canDisplay('\uDE00')) {
                emojiFont = testFont;
                break;
            }
        }
        if (emojiFont == null) {
            emojiFont = new Font("Arial", Font.PLAIN, 22);
            System.err.println("Warning: No suitable emoji font found. Emojis may not render correctly.");
        }
        regularFont = new Font("Segoe UI", Font.BOLD, 14);

        JLabel title = new JLabel("📂 File Organizer Tool", JLabel.CENTER);
        title.setFont(emojiFont.deriveFont(Font.BOLD, 22));
        title.setForeground(new Color(0, 51, 102));
        add(title, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Activity Log"));
        add(scrollPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
        selectFolderButton = new JButton("📁 Select Folder and Organize");
        selectFolderButton.setFont(emojiFont.deriveFont(Font.BOLD, 14));
        selectFolderButton.setBackground(new Color(70, 130, 180));
        selectFolderButton.setForeground(Color.WHITE);
        selectFolderButton.setFocusPainted(false);
        selectFolderButton.addActionListener(this::onSelectFolder);
        bottomPanel.add(selectFolderButton, BorderLayout.NORTH);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        bottomPanel.add(progressBar, BorderLayout.CENTER);

        currentDirLabel = new JLabel("Organizing: ");
        currentDirLabel.setHorizontalAlignment(JLabel.CENTER);
        currentDirLabel.setFont(regularFont);
        bottomPanel.add(currentDirLabel, BorderLayout.SOUTH);

        bottomPanel.setBorder(new EmptyBorder(10, 20, 20, 20));
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void onSelectFolder(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedDir = chooser.getSelectedFile();
            currentDirLabel.setText("Organizing: " + selectedDir.getAbsolutePath());
            selectFolderButton.setEnabled(false);
            new FileOrganizerWorker(selectedDir.toPath()).execute();
        }
    }

    private class FileOrganizerWorker extends SwingWorker<Void, Integer> {
        private final Path directory;
        private final Map<String, List<String>> fileCategories = new HashMap<>();
        private int totalFiles = 0;
        private int processedFiles = 0;

        public FileOrganizerWorker(Path directory) {
            this.directory = directory;
            initializeCategories();
            try (Stream<Path> files = Files.list(directory)) {
                totalFiles = (int) files.filter(Files::isRegularFile).count();
            } catch (IOException ex) {
                publish(-1); // Indicate error in counting
                ex.printStackTrace();
            }
        }

        private void initializeCategories() {
            fileCategories.put("Documents/PDFs", List.of("pdf"));
            fileCategories.put("Documents/WordFiles", List.of("doc", "docx"));
            fileCategories.put("Documents/ExcelSheets", List.of("xls", "xlsx"));
            fileCategories.put("Documents/OtherDocs", List.of("txt", "rtf"));
            fileCategories.put("Images", List.of("jpg", "jpeg", "png", "gif"));
            fileCategories.put("Videos", List.of("mp4", "mov", "avi", "mkv"));
            fileCategories.put("Music", List.of("mp3", "wav", "aac"));
            fileCategories.put("Executables", List.of("exe", "msi", "bat", "sh"));
            fileCategories.put("WebFiles", List.of("html", "css", "js"));
            fileCategories.put("Archives", List.of("zip", "rar", "tar", "gz"));
            fileCategories.put("BinFiles", List.of("bin", "dat"));
            fileCategories.put("Others", List.of());
        }

        @Override
        protected Void doInBackground() throws IOException, InterruptedException {
            try (Stream<Path> files = Files.list(directory)) {
                List<Path> fileList = files.filter(Files::isRegularFile).toList();

                for (Path file : fileList) {
                    String extension = getExtension(file.getFileName().toString()).toLowerCase();
                    String destCategory = getCategoryForExtension(extension);

                    Path destDir = directory.resolve(destCategory);
                    Files.createDirectories(destDir);

                    Path destPath = destDir.resolve(file.getFileName());
                    Files.move(file, destPath, StandardCopyOption.REPLACE_EXISTING);

                    String logMessage = "Moved " + file.getFileName() + " → " + destCategory;
                    SwingUtilities.invokeLater(() -> logArea.append(logMessage + "\n"));

                    processedFiles++;
                    int progress = (totalFiles > 0) ? (int) ((processedFiles / (double) totalFiles) * 100) : 0;
                    publish(progress);
                    Thread.sleep(100);
                }
            } catch (IOException | InterruptedException ex) {
                SwingUtilities.invokeLater(() -> logArea.append("Error: " + ex.getMessage() + "\n"));
                throw ex;
            }
            return null;
        }

        private String getExtension(String fileName) {
            int index = fileName.lastIndexOf(".");
            return index != -1 ? fileName.substring(index + 1) : "";
        }

        private String getCategoryForExtension(String ext) {
            for (var entry : fileCategories.entrySet()) {
                if (entry.getValue().contains(ext)) return entry.getKey();
            }
            return "Others";
        }

        @Override
        protected void process(List<Integer> chunks) {
            if (!chunks.isEmpty()) {
                int latestProgress = chunks.get(chunks.size() - 1);
                if (latestProgress != -1) {
                    progressBar.setValue(latestProgress);
                }
            }
        }

        @Override
        protected void done() {
            selectFolderButton.setEnabled(true);
            if (!isCancelled()) {
                try {
                    get();
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(100);
                        JOptionPane.showMessageDialog(SmartFileOrganizer.this, "🎉 Organization Complete!");
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(SmartFileOrganizer.this, "⚠️ Organization Interrupted!"));
                } catch (java.util.concurrent.ExecutionException e) {
                    Throwable cause = e.getCause();
                    String baseErrorMessage = "⚠️ Organization Failed!";
                    String finalErrorMessage;
                    if (cause != null) {
                        finalErrorMessage = baseErrorMessage + "\\nError: " + cause.getMessage();
                        cause.printStackTrace();
                    } else {
                        finalErrorMessage = baseErrorMessage;
                    }
                    String messageToShow = finalErrorMessage;
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(SmartFileOrganizer.this, messageToShow));
                }
            } else {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(SmartFileOrganizer.this, "🚫 Organization Cancelled!"));
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SmartFileOrganizer app = new SmartFileOrganizer();
            app.setVisible(true);
        });
    }
}
