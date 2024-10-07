package org.example;

import com.fazecast.jSerialComm.SerialPort;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.List;

public class SerialPortGUI extends JFrame {
    private static final Logger logger = Logger.getLogger(SerialPortGUI.class.getName());
    private static int instanceCount; // Static counter for instances
    static {
        String instanceCountStr = Config.getProperty("INSTANCE_COUNT");
        if (instanceCountStr != null) {
            try {
                instanceCount = Integer.parseInt(instanceCountStr);
            } catch (NumberFormatException e) {
                instanceCount = 0; // Default value if parsing fails
            }
        } else {
            instanceCount = 0; // Default value if property is not set
        }
    }

    private final JTextArea textArea;
    private final JTextArea sentTextArea;
    private final JTextArea receivedTextArea;
    private final JComboBox<String> sendPortComboBox;
    private final JComboBox<String> receivePortComboBox;
    private final JLabel statusLabel;
    private int sendCount = 0;

    private SerialPort comPort1;
    private SerialPort comPort2;

    // SerialPortGUI.java
public SerialPortGUI() {
    setTitle("Serial Port Communication");
    setSize(700, 500);
    setMinimumSize(new Dimension(630, 300));
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    // Set location based on instance count
    setLocation(instanceCount * getWidth(), 100);
    instanceCount++;
    Config.setProperty("INSTANCE_COUNT", String.valueOf(instanceCount));

    // Add shutdown hook to decrement instanceCount
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        instanceCount = 0;
        Config.setProperty("INSTANCE_COUNT", String.valueOf(instanceCount));
    }));

    textArea = new JTextArea(2, 20);
    textArea.setLineWrap(true);
    textArea.setWrapStyleWord(true);
    JScrollPane textAreaScrollPane = new JScrollPane(textArea);
    sentTextArea = new JTextArea();
    sentTextArea.setEditable(false);
    receivedTextArea = new JTextArea();
    receivedTextArea.setEditable(false);
    receivedTextArea.setLineWrap(true); // Включить перенос строк
    receivedTextArea.setWrapStyleWord(true); // Перенос по границам слов

    sendPortComboBox = new JComboBox<>();
    sendPortComboBox.setBorder(BorderFactory.createEmptyBorder(0,0,0,30));
    receivePortComboBox = new JComboBox<>();
    updatePortList();

    sendPortComboBox.addActionListener(e -> openSendPort());
    receivePortComboBox.addActionListener(e -> openReceivePort());

    JPanel inputPanel = new JPanel(new BorderLayout());
    inputPanel.add(new JLabel("Input window:"), BorderLayout.NORTH);
    inputPanel.add(textAreaScrollPane, BorderLayout.CENTER);

    JPanel portPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
    portPanel.setBorder(BorderFactory.createTitledBorder("Control window"));
    portPanel.add(new JLabel("Select sending COM Port:"));
    portPanel.add(sendPortComboBox);
    portPanel.add(new JLabel("Select receiving COM Port:"));
    portPanel.add(receivePortComboBox);
    inputPanel.add(portPanel, BorderLayout.SOUTH);

    JPanel sentPanel = new JPanel(new BorderLayout());
    sentPanel.add(new JLabel("Debug Window:"), BorderLayout.NORTH);
    sentPanel.add(new JScrollPane(sentTextArea), BorderLayout.CENTER);

    JPanel receivedPanel = new JPanel(new BorderLayout());
    receivedPanel.add(new JLabel("Output window:"), BorderLayout.NORTH);
    receivedPanel.add(new JScrollPane(receivedTextArea), BorderLayout.CENTER);

    JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sentPanel, receivedPanel);
    splitPane.setResizeWeight(0.5);

    statusLabel = new JLabel("No info.");
    JPanel statusPanel = new JPanel(new BorderLayout());
    statusPanel.setBorder(BorderFactory.createTitledBorder("Status Window"));
    statusPanel.add(statusLabel, BorderLayout.CENTER);

    setLayout(new BorderLayout());
    add(inputPanel, BorderLayout.NORTH);
    add(splitPane, BorderLayout.CENTER);
    add(statusPanel, BorderLayout.SOUTH);

    textArea.addKeyListener(new KeyAdapter() {
    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            if (e.isShiftDown()) {
                textArea.setText(textArea.getText() + System.lineSeparator());
            } else {
                e.consume(); // Prevent the default newline behavior
                sendData();
            }
        }
    }
});

    sendPortComboBox.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
        @Override
        public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
            updatePortList();
        }

        @Override
        public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
            updatePortList();
        }

        @Override
        public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
            // Do nothing
        }
    });

    receivePortComboBox.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
        @Override
        public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
            updatePortList();
        }

        @Override
        public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
            updatePortList();
        }

        @Override
        public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
            // Do nothing
        }
    });

    Timer portReadTimer = new Timer(100, e -> readFromReceivePort());
    portReadTimer.start();
}

    private void readFromReceivePort() {
    if (comPort2 != null && comPort2.isOpen()) {
        new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                while (comPort2.bytesAvailable() > 0) {
                    byte[] readBuffer = new byte[1024];
                    int numRead = comPort2.getInputStream().read(readBuffer);
                    if (numRead > 0) {
                        publish(new String(readBuffer, 0, numRead));
                    }
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String chunk : chunks) {
                    receivedTextArea.append(chunk);
                }
            }

            @Override
            protected void done() {
                // Handle any cleanup or final steps if needed
            }
        }.execute();
    }
}

   private void updatePortList() {
    String selectedSendPort = (String) sendPortComboBox.getSelectedItem();
    String selectedReceivePort = (String) receivePortComboBox.getSelectedItem();

    // Remove all action listeners
    for (ActionListener al : sendPortComboBox.getActionListeners()) {
        sendPortComboBox.removeActionListener(al);
    }
    for (ActionListener al : receivePortComboBox.getActionListeners()) {
        receivePortComboBox.removeActionListener(al);
    }

    sendPortComboBox.removeAllItems();
    receivePortComboBox.removeAllItems();

    // Add "None" item
    sendPortComboBox.addItem("None");
    receivePortComboBox.addItem("None");

    SerialPort[] ports = SerialPort.getCommPorts();
    for (SerialPort port : ports) {
        String portName = port.getSystemPortName();
        if (portName.matches("COM\\d+") && !portName.equals("COM5") && !portName.equals("COM6")) {
            int portNumber = Integer.parseInt(portName.replaceAll("\\D", ""));
            SerialPort tempPort = SerialPort.getCommPort(portName);
            boolean isOpen = tempPort.openPort();
            tempPort.closePort();
            if (isOpen) {
                if ((selectedReceivePort == null || selectedReceivePort.equals("None")
                        || portNumber != Integer.parseInt(selectedReceivePort.replaceAll("\\D", "")) - 1)
                    && (selectedReceivePort == null || selectedReceivePort.equals("None")
                        || portNumber != Integer.parseInt(selectedReceivePort.replaceAll("\\D", "")) - 2)) {
                    sendPortComboBox.addItem(portName);
                }
                if (!portName.equals("COM1") && (selectedSendPort == null || selectedSendPort.equals("None")
                        || portNumber != Integer.parseInt(selectedSendPort.replaceAll("\\D", "")) + 2)
                        && (selectedSendPort == null || selectedSendPort.equals("None")
                        || portNumber != Integer.parseInt(selectedSendPort.replaceAll("\\D", "")) + 1)) {
                    receivePortComboBox.addItem(portName);
                }
            }
        }
    }

    // Add selected ports back to the list
    if (selectedSendPort != null && !selectedSendPort.equals("None")) {
        sendPortComboBox.addItem(selectedSendPort);
    }
    if (selectedReceivePort != null && !selectedReceivePort.equals("None")) {
        receivePortComboBox.addItem(selectedReceivePort);
    }

    setSelectedPort(sendPortComboBox, selectedSendPort);
    setSelectedPort(receivePortComboBox, selectedReceivePort);

    // Add action listeners back
    sendPortComboBox.addActionListener(e -> openSendPort());
    receivePortComboBox.addActionListener(e -> openReceivePort());
}

private void setSelectedPort(JComboBox<String> comboBox, String selectedPort) {
    if (selectedPort != null && comboBox.getItemCount() > 0) {
        boolean portExists = false;
        for (int i = 0; i < comboBox.getItemCount(); i++) {
            if (comboBox.getItemAt(i).equals(selectedPort)) {
                portExists = true;
                break;
            }
        }
        comboBox.setSelectedItem(portExists ? selectedPort : null);
    } else {
        comboBox.setSelectedItem(null);
    }
}

private void openSendPort() {
    String selectedSendPort = (String) sendPortComboBox.getSelectedItem();
    if (comPort1 != null && comPort1.isOpen()) {
        comPort1.closePort();
        sentTextArea.append("Sending port closed.\n");
    }

    if (selectedSendPort != null && !selectedSendPort.equals("None")) {
        comPort1 = SerialPort.getCommPort(selectedSendPort);
        configurePort(comPort1, SerialPort.TIMEOUT_WRITE_BLOCKING);
        if (comPort1.openPort()) {
            sentTextArea.append("Sending port opened.\n");
            statusLabel.setText("Opened " + selectedSendPort + " with baud rate 9600, data bits 8, stop bits 1, no parity.");
        } else {
            sentTextArea.append("Failed to open sending port.\n");
        }
    }
}

private void openReceivePort() {
    String selectedReceivePort = (String) receivePortComboBox.getSelectedItem();
    if (comPort2 != null && comPort2.isOpen()) {
        comPort2.closePort();
        sentTextArea.append("Receiving port closed.\n");
    }

    if (selectedReceivePort != null && !selectedReceivePort.equals("None")) {
        comPort2 = SerialPort.getCommPort(selectedReceivePort);
        configurePort(comPort2, SerialPort.TIMEOUT_READ_SEMI_BLOCKING);
        if (comPort2.openPort()) {
            sentTextArea.append("Receiving port opened.\n");
            statusLabel.setText("Opened " + selectedReceivePort + " with baud rate 9600, data bits 8, stop bits 1, no parity.");
        } else {
            sentTextArea.append("Failed to open receiving port.\n");
        }
    }
}

    private void sendData() {
    String dataToSend = textArea.getText();
    if (dataToSend.isEmpty()) {
        JOptionPane.showMessageDialog(this, "Please enter data to send.", "Error", JOptionPane.ERROR_MESSAGE);
        return;
    }

    if (comPort1 == null || !comPort1.isOpen()) {
        JOptionPane.showMessageDialog(this, "Please ensure the sending COM port is open.", "Error", JOptionPane.ERROR_MESSAGE);
        return;
    }

    // Extract the port number from comPort1
    String sendPortName = comPort1.getSystemPortName();
    int sendPortNumber = Integer.parseInt(sendPortName.replaceAll("\\D", ""));
    String receivePortName = "COM" + (sendPortNumber + 1);

    // Check if COMx+1 is open for reading
    SerialPort receivePort = SerialPort.getCommPort(receivePortName);
    if (receivePort.openPort()) {
        receivePort.closePort();
        JOptionPane.showMessageDialog(this, "The port " + receivePortName + " is not open for receiving.", "Error", JOptionPane.ERROR_MESSAGE);
        return;
    }

    try {
        byte[] dataBytes = dataToSend.getBytes();
        comPort1.getOutputStream().write(dataBytes);
        comPort1.getOutputStream().flush();
        sentTextArea.append("Data sent: " + dataToSend + "\n");

        sendCount++;
        sentTextArea.append("Bytes sent: " + dataBytes.length + "\n");
        statusLabel.setText("Receiving from " + comPort1.getSystemPortName() +  " to " + receivePortName + " with baud rate 9600, data bits 8, stop bits 1, no parity. Send count: " + sendCount);
    } catch (Exception e) {
        logger.log(Level.SEVERE, "Error during communication", e);
        sentTextArea.append("Error: " + e.getMessage() + "\n");
    }
}

    private void configurePort(SerialPort port, int timeout) {
        port.setBaudRate(9600);
        port.setNumDataBits(8);
        port.setNumStopBits(SerialPort.ONE_STOP_BIT);
        port.setParity(SerialPort.NO_PARITY);
        port.setComPortTimeouts(timeout, 1000, 1000);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SerialPortGUI().setVisible(true));
    }
}