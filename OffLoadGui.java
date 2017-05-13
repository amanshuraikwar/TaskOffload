import javax.swing.*;
import java.awt.event.*;
import java.io.*;
import java.util.Scanner;

public class OffLoadGui{
	static JFrame jFrame;
	static final String mountPath = "/home/mounted/nfs/";
	static String pwd;
	static String workerNodeIds[];
	static String commands[] = {"python ", "./"};
	static String imageNames[] = {"python", "c"};
	static String serviceNames[] = {"python-service", "c-service"};
	static String imageLabels[] = {"python", "c"};
	static JDialog progressDialog;
	static JLabel progressDialogLabel;
	static JComboBox comboBox;
	static JTextField filePathTextfield;
	static JButton startOnLocalButton;
	static JButton startOnServerButton;
	static JButton startAutomaticButton;
	static JTextArea outputTextArea;
	static String path;
	static int imageChoice;

	static String currentDockerNodeId;

	public OffLoadGui() {
		
		String temp[] = executeCommand("docker node ls -f \"role=worker\"", null).split("\n");
		workerNodeIds = new String[temp.length - 1];
		for(int i = 1; i<temp.length ; i++) {
			System.out.println(temp[i].split(" ")[0]);
			workerNodeIds[i-1] = temp[i].split(" ")[0];
		}

		currentDockerNodeId  = executeCommand("sudo docker node ls | grep \\*", null).split(" ")[0];
		System.out.println(currentDockerNodeId);

		pwd = executeCommand("pwd", null).split("\n")[0] + "/";

		jFrame = new JFrame("OffLoad");

		JLabel chooseImageLabel = new JLabel();
		chooseImageLabel.setBounds(50, 30, 400, 20);
		chooseImageLabel.setText("Choose Image");

    	comboBox = new JComboBox(imageNames);
    	comboBox.setBounds(50, 50, 400, 50);
    	comboBox.addItemListener(new ItemListener() {
	        public void itemStateChanged(ItemEvent event) {
	            if (event.getStateChange() == ItemEvent.SELECTED) {
					imageChoice = comboBox.getSelectedIndex();
		       }
	        }
	    });

    	JLabel programFilePathLabel = new JLabel();
		programFilePathLabel.setBounds(50, 130, 400, 20);
		programFilePathLabel.setText("Enter file path");

    	filePathTextfield = new JTextField();
        filePathTextfield.setBounds(50, 150, 400, 50);

        JLabel manualButtonsLabel = new JLabel();
		manualButtonsLabel.setBounds(50, 230, 400, 20);
		manualButtonsLabel.setText("Manual start");

		startOnLocalButton = new JButton("Start on local");
        startOnLocalButton.setBounds(50, 250, 200, 50);

        startOnLocalButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e){
                        runLocalButtonOnClick();
                    }
                });

        startOnServerButton = new JButton("Start on server");
        startOnServerButton.setBounds(250, 250, 200, 50);

        startOnServerButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e){
                        runServerButtonOnClick();
                    }
                });

        JLabel automaticButtonsLabel = new JLabel();
		automaticButtonsLabel.setBounds(50, 330, 400, 20);
		automaticButtonsLabel.setText("Automatic start");

		startAutomaticButton = new JButton("Start");
        startAutomaticButton.setBounds(50, 350, 400, 50);

        startAutomaticButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e){
                        runAutomaticButtonOnClick();
                    }
                });

        JLabel outputLabel = new JLabel();
		outputLabel.setBounds(50, 430, 400, 20);
		outputLabel.setText("Output");

		outputTextArea = new JTextArea();
        
        JScrollPane scrollPane = new JScrollPane (outputTextArea, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        scrollPane.setBounds(50, 450, 400, 300);

        jFrame.add(chooseImageLabel);
	    jFrame.add(comboBox);
	    jFrame.add(programFilePathLabel);
	    jFrame.add(filePathTextfield);
	    jFrame.add(manualButtonsLabel);
	    jFrame.add(startOnLocalButton);
	    jFrame.add(startOnServerButton);
	    jFrame.add(automaticButtonsLabel);
	    jFrame.add(startAutomaticButton);
	    jFrame.add(outputLabel);
	    jFrame.add(scrollPane);
	    jFrame.setLayout(null);
	    jFrame.setSize(500,800);

	    progressDialog = new JDialog(jFrame , "Please Wait...", false);
	    progressDialog.setSize(400,200);
	    progressDialog.setResizable(false);
	    progressDialog.setBounds(50, 300, 400, 200);
	    progressDialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

	    progressDialogLabel = new JLabel();
	    progressDialogLabel.setBounds(100, 50, 300, 100);
	    progressDialog.add(progressDialogLabel);
    }
	

	void showDialog(String message) {
		progressDialogLabel.setText(message);
		progressDialog.setVisible(true);
	}

	void runLocalButtonOnClick() {
		path = filePathTextfield.getText().toString();
		Thread t = new Thread(new Runnable() {
			@Override
		    public void run() {
		    	String command = "mkdir " + currentDockerNodeId;
		        executeCommand(command, null);
		        
		        String tempDataPath = pwd + currentDockerNodeId;
		        String pathParts[] = path.split("/");
		        String fileName = pathParts[pathParts.length - 1];

		        command = "sudo cp " + path + " " + tempDataPath + "/";
		        executeCommand(command, null);

		        command="docker run -v " + tempDataPath + ":/data " + imageNames[imageChoice] + " " + commands[imageChoice] + "/data/" + fileName;
				executeCommand(command, outputTextArea);
		    }
		});
		t.start();
	}

	void runServerButtonOnClick() {
		path = filePathTextfield.getText().toString();
		Thread t = new Thread(new Runnable() {
			@Override
		    public void run() {
		    	String tempDataPath = mountPath + currentDockerNodeId;
		    	String command = "mkdir " + tempDataPath;
		        executeCommand(command, null);

		        String pathParts[] = path.split("/");
		        String fileName = pathParts[pathParts.length - 1];

		        command = "cp " + path + " " + tempDataPath + "/";
		        executeCommand(command, null);
		        //--restart-condition \"none\"
		        command = "docker service create --replicas 1 --constraint node.id==" + workerNodeIds[0] + "  --name "+ serviceNames[imageChoice] + " --mount type=bind,source=" + tempDataPath + ",destination=/data " + imageNames[imageChoice] + " " + commands[imageChoice] + "/data/" + fileName;
				executeCommand(command, outputTextArea);

				command = "docker service logs " + serviceNames[imageChoice] + " --follow";
				executeCommand(command, outputTextArea);
		    }
		});
		t.start();
	}

	Thread runLocalThread = new Thread(new Runnable() {
			@Override
		    public void run() {
		    	String command = "mkdir " + currentDockerNodeId;
		        executeCommand(command, null);
		        
		        String tempDataPath = pwd + currentDockerNodeId;
		        String pathParts[] = path.split("/");
		        String fileName = pathParts[pathParts.length - 1];

		        command = "sudo cp " + path + " " + tempDataPath + "/";
		        executeCommand(command, null);

		        command="docker run --name " + serviceNames[imageChoice] + " -v " + tempDataPath + ":/data " + imageNames[imageChoice] + " " + commands[imageChoice] + "/data/" + fileName;
				executeCommand(command, outputTextArea);
		    }
		});

	Thread monitorStatsThread = new Thread(new Runnable() {
			float memStats = new float[5];
			float cpuStats = new float[5];

			@Override
			public void run() {
				int i = 0;
				while(true) {
					try {
						
						if(!runLocalThread.isAlive()) {
							return;
						}

						String command = "docker stats " + serviceNames[imageChoice] + " --no-stream --format '{{.CPUPerc}} {{.MemPerc}}'";
						String stats[] = executeCommand(command, outputTextArea).split(" ");

						float cpuStats[i] = Float.parseFloat(stats[0].split("%")[0]);
						float memStats[i] = Float.parseFloat(stats[1].split("%")[0]);

						if(i == 4) {
							float avg = 0;
							for(float cpuStat : cpuStats) {
								avg += cpuStat;
							}
							avg = avg/cpuStats.length;

							if(avg > 80) {
								break;
							}

							avg = 0;
							for(float memStat : memStats) {
								avg += memStat;
							}
							avg = avg/memStats.length;

							if(avg > 80) {
								break;
							}
							
							i = 0;
						}

						i++;

						Thread.sleep(2800);
					}catch(Exception e) {
						e.printStackTrace();
					}
				}

				if(!runLocalThread.isAlive()) {
					String command = "docker stop " + serviceNames[imageChoice];
					executeCommand(command);
				}

			}
		});

	void runAutomaticButtonOnClick() {
		path = filePathTextfield.getText().toString();

		runLocalThread.start();
		monitorStatsThread.start();
	}

	public static void main(String[] args) {
	    new OffLoadGui();
	    jFrame.setVisible(true);
    }

	public String executeCommand(String command, JTextArea outputTextArea) {
		String stdOutput = "";
		System.out.println(command);
		String[] commands = new String[]{"/bin/bash", "-c", command};
		try {
			Process proc = new ProcessBuilder(commands).start();
			
			BufferedReader stdInput = new BufferedReader(new
			InputStreamReader(proc.getInputStream()));

			BufferedReader stdError = new BufferedReader(new
			InputStreamReader(proc.getErrorStream()));

			String s = null;
			while ((s = stdInput.readLine()) != null) {
				if(outputTextArea != null) {
					outputTextArea.setText(outputTextArea.getText().toString()+s+"\n");	
				}
				stdOutput += s + "\n";
				System.out.println(s);
			}

			while ((s = stdError.readLine()) != null) {
				if(outputTextArea != null) {
					outputTextArea.setText(outputTextArea.getText().toString()+s+"\n");	
				}
				stdOutput += s + "\n";
				System.out.println(s);
			}
			return stdOutput;
		} catch (Exception e) {
			e.printStackTrace();
			return stdOutput;
		}
	}
}
