package org.example;

import com.fazecast.jSerialComm.SerialPort;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.List;
import java.util.Arrays;

public class SerialPortGUI extends JFrame {
    private static final Logger logger = Logger.getLogger(SerialPortGUI.class.getName());
    private static int instanceCount; // Static counter for instances
    private static final byte[] FLAG = {64, 104}; // '@' and 'h' characters
    private static final int DATA_LENGTH = 8;
    private static final byte ESCAPE = 0x00; // '00' character
    private static final byte ESCAPE_MASK = 0x1B; // 'ESC' character

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
    private final JTextArea statusLabel;
    private int sendCount = 0;

    private SerialPort comPort1;
    private SerialPort comPort2;

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
        sentPanel.add(new JLabel("Debug window:"), BorderLayout.NORTH);
        sentPanel.add(new JScrollPane(sentTextArea), BorderLayout.CENTER);

        JPanel receivedPanel = new JPanel(new BorderLayout());
        receivedPanel.add(new JLabel("Output window:"), BorderLayout.NORTH);
        receivedPanel.add(new JScrollPane(receivedTextArea), BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sentPanel, receivedPanel);
        splitPane.setResizeWeight(0.5);

        statusLabel = new JTextArea(" ");
        statusLabel.setEditable(false);
        statusLabel.setLineWrap(true);
        statusLabel.setWrapStyleWord(true);
        JScrollPane statusScrollPane = new JScrollPane(statusLabel);

        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createTitledBorder("Status Window"));
        statusPanel.add(statusScrollPane, BorderLayout.CENTER);
        statusPanel.setPreferredSize(new Dimension(getWidth(), 100));

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
                        int offset = 0;
                        int fixedPacketLength = 13; // Fixed packet length
                        while (offset < numRead) {
                            if (readBuffer[offset] != FLAG[0] && readBuffer[offset + 1] != FLAG[1]) {
                                offset++; // Incomplete packet, look for the start of the packet
                            }
                            byte[] packet = Arrays.copyOfRange(readBuffer, offset, offset + fixedPacketLength);
                            byte[] unpackedData = byte_destaffing(packet);
                            byte[] paddedData = new byte[unpackedData.length + 4];
                            System.arraycopy(unpackedData, 0, paddedData, 2, unpackedData.length);
                            String dataWithOffset = new String(paddedData).trim();
                            publish(dataWithOffset);
                            offset += fixedPacketLength;
                        }
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
                // Handle completion if necessary
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
    //    SerialPort receivePort = SerialPort.getCommPort(receivePortName);
    //    if (receivePort.openPort()) {
    //        receivePort.closePort();
    //        JOptionPane.showMessageDialog(this, "The port " + receivePortName + " is not open for receiving.", "Error", JOptionPane.ERROR_MESSAGE);
    //        return;
    //    }

    if (comPort2 == null || !comPort2.isOpen()) {
        int response = JOptionPane.showConfirmDialog(this, "The receiving COM port in this program is not opened.\nDo you want to continue sending?", "Warning", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (response != JOptionPane.YES_OPTION) {
            return;
        }
    }

    try {
        byte[] dataBytes = dataToSend.getBytes();
        int totalPackets = (int) Math.ceil((double) dataBytes.length / DATA_LENGTH);
        AtomicInteger countPackets = new AtomicInteger();

        for (int i = 0; i < totalPackets; i++) {
            int start = i * DATA_LENGTH;
            int end = Math.min(start + DATA_LENGTH, dataBytes.length);
            byte[] packetData = Arrays.copyOfRange(dataBytes, start, end);
            byte[] packet = createPacket(packetData, sendPortName);
            comPort1.getOutputStream().write(packet);
            comPort1.getOutputStream().flush();
            SwingUtilities.invokeLater(() -> {
                countPackets.getAndIncrement();
                statusLabel.setText(statusLabel.getText() + "\nPacket " + countPackets + " : " + packetToString(packet));
            });
        }

        sendCount ++;
        statusLabel.setText("Receiving from " + comPort1.getSystemPortName() + " to " + receivePortName + " with baud rate 9600, data bits 8, stop bits 1, no parity. Send count: " + sendCount);
    } catch (Exception e) {
        logger.log(Level.SEVERE, "Error during communication", e);
        sentTextArea.append("Error: " + e.getMessage() + "\n");
    }
}

    private byte[] createPacket(byte[] data, String sourcePort) {
    byte[] packet = new byte[4 + DATA_LENGTH + 1]; // 2 flags + 1 destination address + 1 source address + data length + 1 FCS
    packet[0] = FLAG[0];
    packet[1] = FLAG[1];
    packet[2] = 0; // Destination Address
    packet[3] = (byte) Integer.parseInt(sourcePort.replaceAll("\\D", ""));
    System.arraycopy(data, 0, packet, 4, data.length);
    Arrays.fill(packet, 4 + data.length, 4 + DATA_LENGTH, (byte) 0); // Pad with zeros if necessary
    packet[4 + DATA_LENGTH] = 0; // FCS
    return applyByteStuffing(packet);
}

    private byte[] applyByteStuffing(byte[] packet) {
    // Извлечение части данных
    byte[] data = Arrays.copyOfRange(packet, 2, packet.length-1);

    // Применение байт-стаффинга к части данных
    byte[] stuffedData = applyByteStuffingToData(data);

    // Восстановление пакета с данными
    byte[] stuffedPacket = new byte[2 + stuffedData.length + 1];
    System.arraycopy(packet, 0, stuffedPacket, 0, 2);
    System.arraycopy(stuffedData, 0, stuffedPacket, 2, stuffedData.length);
    System.arraycopy(packet, packet.length - 1, stuffedPacket, 2 + stuffedData.length, 1);

    return stuffedPacket;
}

    private byte[] applyByteStuffingToData(byte[] data) {
    ByteArrayOutputStream stuffedData = new ByteArrayOutputStream();
    for (int i = 0; i < data.length; i++) {
        if (data[i] == '@' && i + 1 < data.length && data[i + 1] == 'h') {
            stuffedData.write('@' ^ ESCAPE);
            stuffedData.write(ESCAPE_MASK);
            i++; // Пропустить следующий символ, так как он является частью последовательности
        } else {
            stuffedData.write(data[i]);
        }
    }
    return stuffedData.toByteArray();
}

private byte[] byte_destaffing(byte[] packet) {
    byte[] data = Arrays.copyOfRange(packet, 2, packet.length - 1);
    ByteArrayOutputStream unstuffedData = new ByteArrayOutputStream();
    for (int i = 0; i < data.length; i++) {
        if (i < data.length - 1 && data[i] == ('@' ^ ESCAPE) && data[i + 1] == (ESCAPE_MASK) && data[i + 1] != ('h')) {
            unstuffedData.write('@');
            unstuffedData.write('h');
            i++; // Skip the next character as it is part of the sequence
        } else {
            unstuffedData.write(data[i]);
        }
    }
    return unstuffedData.toByteArray();
}

    private String packetToString(byte[] packet) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < packet.length; i++) {
        if (i < 2) {
            sb.append((char) packet[i]); // Convert first 2 bytes to characters
        } else if (i < 4) {
            sb.append(packet[i] & 0xFF); // Convert 3rd and 4th bytes to decimal
        } else {
            if (i < packet.length - 1 && packet[i] == ('@' ^ ESCAPE) && packet[i + 1] == (ESCAPE_MASK)) {
                sb.append("[0x").append(String.format("%02X", ESCAPE)).append(" 0x").append(String.format("%02X", ESCAPE_MASK)).append("]");
                i++; // Skip the next character as it is part of the sequence
            } else {
                if ((packet[i] & 0xFF) == 0) {
                    sb.append((packet[i] & 0xFF)); // Convert remaining bytes to decimal if value is 00
                } else {
                    sb.append((char) packet[i]); // Convert remaining bytes to characters
                }
            }
        }
    }
    return sb.toString().trim(); // Remove the trailing space
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