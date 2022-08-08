import client.*;

import javax.swing.*;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * This is just some example code to show you how to interact
 * with the server using the provided client and two queues.
 * Feel free to modify this code in any way you like!
 */

public class MyProtocol {

    // The host to connect to. Set this to localhost when using the audio interface tool.
    private static String SERVER_IP = "netsys2.ewi.utwente.nl"; //"127.0.0.1";
    // The port to connect to. 8954 for the simulation server.
    private static int SERVER_PORT = 8954;
    // The frequency to use.
    private static int frequency = 10000;//TODO: Set this to your group frequency!
    private byte myIP = (byte) (new Random().nextInt(69) + 1);
    private BlockingQueue<Message> receivedQueue;
    private BlockingQueue<Message> sendingQueue;
    private ArrayList<Byte> confirmed_ack = new ArrayList<Byte>();
    private ArrayList<Byte> confirmed_packets = new ArrayList<Byte>();
    private ArrayList<Byte> neighbours_short = new ArrayList<Byte>();
    private HashMap<Byte, ArrayList<Byte>> neighbours_long = new HashMap<>();
    private boolean locked = false;
    private String for_printing = "";
    private HashMap<Integer, ByteBuffer> fragmented_packets = new HashMap<>();
    private int packet_to_read = 1;
    private Byte waitingFor = 1;
    private HashMap<Byte,ArrayList<Byte>>finito_packets = new HashMap<>();
    //private HashMap<Integer,ArrayList<Byte>>confirmed = new HashMap<>();


    public MyProtocol(String server_ip, int server_port, int frequency) {
        receivedQueue = new LinkedBlockingQueue<Message>();
        sendingQueue = new LinkedBlockingQueue<Message>();

        new Client(SERVER_IP, SERVER_PORT, frequency, receivedQueue, sendingQueue); // Give the client the Queues to use

        new receiveThread(receivedQueue).start(); // Start thread to handle received messages!

        // handle sending from stdin from this thread.
        try {
            ByteBuffer temp = ByteBuffer.allocate(1024);
            int read = 0;
            System.out.println("My IP is: " + myIP);
            System.out.println("STARTING NODES ADDRESSING SETUP");
            InitialTimer();
            NodesAddressing();
            System.out.println("YOU CAN START CHATTING NOW");
            int packet_to_read = 1;
            while (true) {
                System.out.print("Enter your message here: ");
                read = System.in.read(temp.array()); // Get data from stdin, hit enter to send!
                byte[] saver = new byte[1025];
                saver = temp.array();
                if (read == 1024) {
                    temp.array()[read] = 127;
                    saver[read] = 127;
                } else {
                    temp.array()[read - 1] = 127;
                    saver[read - 1] = 127;
                }/*
                for (int k = 0; k < 32; k++) {
                    System.out.print(saver[k] + " ");
                }
                System.out.println();
                for (int k = 0; k < 32; k++) {
                    System.out.print(temp.array()[k] + " ");
                }
                */

                Fragmentation(temp.array(), read);


                //ByteBuffer toSend = ByteBuffer.allocate(32); // jave includes newlines in System.in.read, so -2 to ignore this
                //toSend.put(saver, 0, 32); // jave includes newlines in System.in.read, so -2 to ignore this
                //Message msg = new Message(MessageType.DATA,toSend);
                //sendingQueue.put(msg);
                Thread.sleep(new Random().nextInt(500));
                for (int i = 0; i < fragmented_packets.keySet().size(); i++) {
                    confirmed_ack.clear();
                    Message msg = new Message(MessageType.DATA, fragmented_packets.get(i));

                    WaitForFreeChannel();
                    //packet_to_read++;
                    //while (confirmed_ack.size() < 3) {
                        //System.out.println("Confirmed: " + confirmed_ack.size());
                        sendingQueue.put(msg);
                        Thread.sleep(15000);
                    //}
                }
                fragmented_packets.clear();
            }
        } catch (InterruptedException e) {
            System.exit(2);
        } catch (IOException e) {
            System.exit(2);
        }
    }

    public void Fragmentation(byte[] message, int read) {
        int current_packet = 0;
        int last_position = 0;
        int number_of_fragments = read / 29 + 1;
        byte[] arr = new byte[33];
        arr[0] = 125;
        arr[1] = myIP;
        int l;
        for (int i = 0; i < number_of_fragments; i++) {
            l = 3;
            //arr[2] = (byte) current_packet;
            arr[2] = (byte) packet_to_read;
            for (int j = last_position; j < last_position + 29; j++) {
                arr[l] = message[j];
                if (message[j] == 127) {
                    break;
                }
                l++;
            }
            last_position += 29;
            /*System.out.println("fragment");
            for (int k = 0; k < 32; k++) {
                System.out.print(arr[k] + " ");
            }

             */
            ByteBuffer toSend = ByteBuffer.allocate(32); // jave includes newlines in System.in.read, so -2 to ignore this
            toSend.put(arr, 0, 32);
            fragmented_packets.put(current_packet, toSend);
            packet_to_read++;
            current_packet++;
            //fragmented_packets.put(current_packet, toSend);
            //current_packet++;
        }
        //System.out.println("Last position " + last_position);
        //System.out.println("keyset size " + fragmented_packets.keySet().size());

    }

    public void WaitForFreeChannel() {
        try {
            Thread.sleep(new Random().nextInt(1200));
            if (locked == true) {
                System.out.println("Please wait until the link is free");
                while (locked == true) {
                    Thread.sleep(500);
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void PrimitiveSending(int read, byte[] saver) throws InterruptedException {
        if (read <= 32) {
            System.out.println("Read: " + read);
            read++;
            saver[read - 2] = 127;
            for (int i = 0; i < saver.length; i++) {
                System.out.print("Pos " + i + ": " + saver[i] + " ");
            }
            System.out.println();
            System.out.println("Read: " + read);

            System.out.println("temp array len: " + saver.length);
            if (read > 0) {
                ByteBuffer toSend = ByteBuffer.allocate(read - 1); // jave includes newlines in System.in.read, so -2 to ignore this
                toSend.put(saver, 0, read - 1); // jave includes newlines in System.in.read, so -2 to ignore this
                Message msg;
                if ((read - 1) > 2) {
                    msg = new Message(MessageType.DATA, toSend);
                } else {
                    msg = new Message(MessageType.DATA_SHORT, toSend);
                }
                sendingQueue.put(msg);
            }
        } else {
            boolean fragmentation_done = false;
            int len = 0;
            while (fragmentation_done == false) {
                System.out.println("Len: " + len);
                if (read >= 33) {
                    byte[] arr = new byte[33];
                    int j = 0;
                    for (int i = len; i < len + 32; i++) {
                        arr[j] = saver[i];
                        j++;
                    }
                    len += 32;
                    ByteBuffer toSend = ByteBuffer.allocate(32); // jave includes newlines in System.in.read, so -2 to ignore this
                    toSend.put(arr, 0, 32); // jave includes newlines in System.in.read, so -2 to ignore this
                    Message msg;

                    msg = new Message(MessageType.DATA, toSend);

                    sendingQueue.put(msg);
                    read -= 32;
                } else {
                    System.out.println("Small fragment");
                    System.out.println("Read: " + read);
                    byte[] arr = new byte[saver.length];
                    int j = 0;
                    for (int i = len; i < len + 32; i++) {
                        arr[j] = saver[i];
                        j++;
                    }
                    read++;
                    arr[read - 2] = 127;
                    for (int i = 0; i < saver.length; i++) {
                        System.out.print("Pos " + i + ": " + saver[i] + " ");
                    }
                    System.out.println("Len: " + len);
                    System.out.println("Read: " + read);

                    System.out.println("temp array len: " + arr.length);

                    ByteBuffer toSend = ByteBuffer.allocate(32); // jave includes newlines in System.in.read, so -2 to ignore this
                    toSend.put(arr, 0, 32); // jave includes newlines in System.in.read, so -2 to ignore this
                    Message msg;
                    msg = new Message(MessageType.DATA, toSend);
                    sendingQueue.put(msg);
                    fragmentation_done = true;
                }
            }
        }
    }

    public void AddressPrinting() {
        System.out.print("My Neighbours ");
        for (int i = 0; i < neighbours_short.size(); i++) {
            System.out.print(neighbours_short.get(i) + " ");
        }
        System.out.println();
        for (byte b : neighbours_long.keySet()) {
            System.out.print("Neighbours of " + b + ": ");
            ArrayList<Byte> list_of_neigbours = neighbours_long.get(b);
            for (int i = 0; i < list_of_neigbours.size(); i++) {
                System.out.print(list_of_neigbours.get(i) + " ");
            }
            System.out.println();
        }
    }

    public void InitialTimer() {
        long startTime = System.currentTimeMillis();
        System.out.println("Initial timer started");
        while (System.currentTimeMillis() - startTime < 5000) {
        }
        System.out.println("Initial timer done");
    }

    public void NodesAddressing() throws InterruptedException {
        neighbours_short.clear();
        neighbours_long.clear();
        long startTime = System.currentTimeMillis();
        int current_position = 0;
        while (System.currentTimeMillis() - startTime < 60000) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            long elapsedSeconds = elapsedTime / 1000;
            //System.out.println(elapsedSeconds);
            //System.out.println("DOING ADDRESSING STILL " + elapsedSeconds);
            boolean done = false;
            current_position = 2;

            byte[] arr = new byte[6];
            arr[0] = 95;
            arr[1] = myIP;
            arr[2] = 0;
            arr[3] = 0;
            arr[4] = 0;
            if (neighbours_short.isEmpty() == false) {
                for (int i = 0; i < neighbours_short.size(); i++) {
                    arr[current_position] = neighbours_short.get(i);
                    current_position++;
                }
            }
            ByteBuffer toSend = ByteBuffer.allocate(5); // jave includes newlines in System.in.read, so -2 to ignore this
            toSend.put(arr, 0, 5); // jave includes newlines in System.in.read, so -2 to ignore this
            Message msg;
            msg = new Message(MessageType.DATA, toSend);
            Thread.sleep(new Random().nextInt(8000) + 1000);
            if (locked == true) {
                //System.out.println("Waiting for free line");
                while (locked == true) {
                    Thread.sleep(500);
                }
            }
            sendingQueue.put(msg);
        }
        AddressPrinting();
        System.out.println();
        System.out.println("ADRRESS SETUP COMPLETE!");
    }

    public static void main(String args[]) {
        if (args.length > 0) {
            frequency = Integer.parseInt(args[0]);
        }
        new MyProtocol(SERVER_IP, SERVER_PORT, frequency);
    }

    private class receiveThread extends Thread {
        private BlockingQueue<Message> receivedQueue;

        public receiveThread(BlockingQueue<Message> receivedQueue) {
            super();
            this.receivedQueue = receivedQueue;
        }

        public void printByteBuffer(ByteBuffer bytes, int bytesLength) {
            for (int i = 0; i < bytesLength; i++) {
                System.out.print(Byte.toString(bytes.get(i)) + " ");
            }
            System.out.println();
        }

        public void AddressDataHandling(Message m) {
            byte[] arr = m.getData().array();
            byte flag = arr[0];
            byte neighbour = arr[1];
            if (neighbours_short.contains(neighbour) == false) {
                neighbours_short.add(neighbour);
            }
            if (neighbours_long.containsKey(neighbour) == false) {
                ArrayList<Byte> template = new ArrayList<Byte>();
                neighbours_long.put(neighbour, template);
            }
            ArrayList<Byte> modified = neighbours_long.get(neighbour);
            for (int i = 2; i <= 4; i++) {
                if (arr[i] != 0) {
                    if (modified.contains(arr[i]) == false) {
                        modified.add(arr[i]);
                    }
                }
            }
            neighbours_long.replace(neighbour, modified);
        }

        public void printData2(Message m) {
            String s = "";
            boolean done = false;
            byte[] arr = m.getData().array();
            byte[] arr1 = new byte[m.getData().capacity()];
            for (int i = 0; i < m.getData().capacity(); i++) {
                if (arr[i] == 127) {
                    s = new String(arr1, StandardCharsets.UTF_8);
                    for_printing += s;
                    System.out.println("Message length: " + s.length());
                    System.out.println("Message: " + for_printing);
                    //System.out.println();
                    for_printing = "";
                    done = true;
                    break;
                } else {
                    arr1[i] = arr[i];
                }
            }
            if (done == false) {
                s = new String(arr1, StandardCharsets.UTF_8);
                for_printing += s;
            }
            //System.out.print(s);
        }

        public void printData3(Message m) {
            String s = "";
            int k = 0;
            boolean done = false;
            byte[] arr = m.getData().array();
            byte[] arr1 = new byte[29];
            for (int i = 3; i < 32; i++) {
                if (arr[i] == 127) {
                    s = new String(arr1, StandardCharsets.UTF_8);
                    for_printing += s;
                    //System.out.println("Message length: " + s.length());
                    System.out.println("Message: " + for_printing);
                    //System.out.println();
                    //confirmed_packets.clear();
                    System.out.print("Enter your message here: ");
                    for_printing = "";
                    done = true;
                    break;
                } else {
                    arr1[k] = arr[i];
                    k++;
                }
            }
            if (done == false) {
                s = new String(arr1, StandardCharsets.UTF_8);
                for_printing += s;
            }
            //System.out.print(s);
        }

        public void run() {
            while (true) {
                try {
                    Message m = receivedQueue.take();
                    if (m.getType() == MessageType.BUSY) {
                        //System.out.println("BUSY");
                        locked = true;
                    } else if (m.getType() == MessageType.FREE) {
                        //System.out.println("FREE");
                        locked = false;
                    } else if (m.getType() == MessageType.DATA) {
                        byte flag = m.getData().array()[0];
                        //System.out.println("DATA: ");
                        //printByteBuffer(m.getData(), m.getData().capacity()); //Just print the data
                        if (flag == 95) {
                            //printByteBuffer(m.getData(), m.getData().capacity()); //Just print the data
                            //System.out.println("RECEIVING ADDRESS DATA");
                            AddressDataHandling(m);
                            //AddressPrinting();
                            //System.out.println();
                        } else if (flag == 63) {
                            System.out.println("NEW ADDRESS SETUP REQUESTED");
                        } else if (flag == 125) {
                            //System.out.println("RECEIVING A MESSAGE");
                            byte sequence_number = m.getData().array()[2];
                            byte source = m.getData().array()[1];
                            if(!finito_packets.containsKey(source)){
                                ArrayList<Byte> b = new ArrayList<>();
                                finito_packets.put(source,b);
                            }
                            //printByteBuffer(m.getData(), m.getData().capacity()); //Just print the data
                            if (!finito_packets.get(source).contains(sequence_number)) {

                                if (source != myIP) {
                                    confirmed_packets.add(sequence_number);
                                    ArrayList<Byte> f = finito_packets.get(source);
                                    f.add(sequence_number);
                                    finito_packets.replace(source,f);
                                    //waitingFor++;
                                    //packet_to_read++;
                                    printData3(m);
                                    if (!neighbours_long.containsKey(source) && neighbours_short.size() > 1) {
                                        //WaitForFreeChannel();
                                        Thread.sleep(new Random().nextInt(4000) + 1000);
                                        //System.out.println("FORWARDING PACKET, don't have the source");
                                        byte[] cp = m.getData().array();
                                        ByteBuffer b = ByteBuffer.allocate(32); // jave includes newlines in System.in.read, so -2 to ignore this
                                        b.put(cp, 0, 32);
                                        Message msg = new Message(MessageType.DATA, b);
                                        sendingQueue.put(msg);
                                    } else if (neighbours_long.containsKey(source)) {
                                        if (neighbours_long.get(source).size() < 3 && neighbours_short.size() > 1) {
                                            Thread.sleep(new Random().nextInt(4000) + 1000);
                                            //System.out.println("FORWARDING PACKET, have less than 3 neighbours");
                                            byte[] cp = m.getData().array();
                                            ByteBuffer b = ByteBuffer.allocate(32); // jave includes newlines in System.in.read, so -2 to ignore this
                                            b.put(cp, 0, 32);
                                            Message msg = new Message(MessageType.DATA, b);
                                            sendingQueue.put(msg);
                                        }
                                    }

                                    byte[] cp = m.getData().array();
                                    cp[0] = 126;
                                    cp[3] = myIP;
                                    ByteBuffer b = ByteBuffer.allocate(32); // jave includes newlines in System.in.read, so -2 to ignore this
                                    b.put(cp, 0, 32);
                                    Message msg = new Message(MessageType.DATA, b);
                                    Thread.sleep(new Random().nextInt(8000) + 1000);
                                    //System.out.println("RETURNING ACK AGAIN");
                                    //sendingQueue.put(msg);
                                }


                            } else {
                                byte[] cp = m.getData().array();
                                /*
                                if (neighbours_long.get(source).size() < 3 && neighbours_short.size() > 1 && source != myIP) {
                                    //Thread.sleep(1200);
                                    //byte[] cp = m.getData().array();
                                    ByteBuffer b = ByteBuffer.allocate(32); // jave includes newlines in System.in.read, so -2 to ignore this
                                    b.put(cp, 0, 32);
                                    Message msg = new Message(MessageType.DATA, b);
                                    sendingQueue.put(msg);

                                }

                                 */
                                cp[0] = 126;
                                cp[3] = myIP;
                                ByteBuffer b = ByteBuffer.allocate(32); // jave includes newlines in System.in.read, so -2 to ignore this
                                b.put(cp, 0, 32);
                                Message msg = new Message(MessageType.DATA, b);
                                Thread.sleep(new Random().nextInt(8000) + 1000);
                                //System.out.println("RETURNING FIRST ACK");
                                //sendingQueue.put(msg);

                            }

                        } else if (flag == 126) {
                            System.out.println("RECEIVING ACK");
                            printByteBuffer(m.getData(), m.getData().capacity());
                            byte destination = m.getData().array()[1];
                            byte sequence_number = m.getData().array()[2];
                            byte source = m.getData().array()[3];
                            if (destination == myIP) {

                                if (confirmed_ack.contains(source) == false) {
                                    confirmed_ack.add(source);
                                }
                            }
                            if (destination != myIP && neighbours_short.size() > 1) {
                                //Thread.sleep(1500);
                                System.out.println("RETRANSMITTING ACK");
                                Thread.sleep(new Random().nextInt(5000) + 1000);
                                byte[] cp = m.getData().array();
                                ByteBuffer b = ByteBuffer.allocate(32); // jave includes newlines in System.in.read, so -2 to ignore this
                                b.put(cp, 0, 32);
                                Message msg = new Message(MessageType.DATA, b);
                                Thread.sleep(new Random().nextInt(8000) + 1000);
                                System.out.println("RETURNING FIRST ACK");
                                sendingQueue.put(msg);
                                //sendingQueue.put(m);
                            }
                        } else {
                            System.out.println();
                            //System.out.println("Receiving old message now");
                            //printByteBuffer(m.getData(),m.getData().capacity());
                            printData2(m);
                            System.out.print("Enter your message here: ");
                        }
                    } else if (m.getType() == MessageType.DATA_SHORT) {
                        System.out.print("DATA_SHORT: ");
                        printByteBuffer(m.getData(), m.getData().capacity()); //Just print the data
                    } else if (m.getType() == MessageType.DONE_SENDING) {
                        //System.out.println("DONE_SENDING");
                    } else if (m.getType() == MessageType.HELLO) {
                        //System.out.println("HELLO");
                    } else if (m.getType() == MessageType.SENDING) {
                        //System.out.println("SENDING");
                    } else if (m.getType() == MessageType.END) {
                        System.out.println("END");
                        System.exit(0);
                    }
                } catch (InterruptedException e) {
                    System.err.println("Failed to take from queue: " + e);
                }
            }
        }
    }
}

