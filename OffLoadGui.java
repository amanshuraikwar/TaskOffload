import javax.swing.*;
import java.awt.event.*;
import java.io.*;
import java.util.Scanner;

public class OffLoadGui{
	static JFrame jFrame;
	static final String mountPath = "/home/mounted/nfs/";
	static String pwd;
	static String workerNodeIds[];
	
	static String imageNames[] = {"python", "progrium/stress"};
	
	static String serviceNames[] = {"python-service", "stress-service"};
	static String imageLabels[] = {"python", "progrium-stress"};
	static JDialog progressDialog;
	static JLabel progressDialogLabel;
	static JComboBox comboBox;
	static JTextField filePathTextfield;
	static JTextField argsTextfield;
	static JButton startOnLocalButton;
	static JButton startOnServerButton;
	static JButton startAutomaticButton;
	static JTextArea outputTextArea;
	static String path;
	static int imageChoice;
	static JButton stopContainerButton;
	static JButton discoverServersButton;
	static JLabel noOfServersLabel;

	static JDialog alertDialog;
	static JButton alertDialogDismissButton;
	static JLabel alertDialogLabel;

	static String currentDockerNodeId;
	static float threshold = 60;
	static String currentServiceName = null;
	static String currentImageName = null;
	static String currentArgs = null;

	static Thread runOnServerThread, runLocalThread, monitorStatsThread;
	static Runnable runOnServerRunnable, runLocalRunnable, monitorStatsRunnable;

	public OffLoadGui() {
		
		runOnServerRunnable = new Runnable() {
			@Override
		    public void run() {
		    	String tempDataPath = mountPath + currentServiceName;
		    	String command = "mkdir " + tempDataPath;
		        executeCommand(command, null);

		        // command = "rm " + tempDataPath + "/*";
		        // executeCommand(command, null);

		        command = "cp " + path + "/* " + tempDataPath + "/";
		        executeCommand(command, null);

		        command = "docker service create --replicas 1 --constraint node.id==" 
		        + workerNodeIds[0] + " --restart-condition \"none\" --name "+ currentServiceName 
		        + " --mount type=bind,source=" + tempDataPath + ",destination=/data " 
		        + currentImageName + " " + currentArgs;

				executeCommand(command, outputTextArea);

				command = "docker service logs " + currentServiceName + " --follow";
				executeCommand(command, outputTextArea);
		    }
		};

		runLocalRunnable = new Runnable() {
			@Override
		    public void run() {
		    	String tempDataPath = mountPath + currentServiceName;
		    	String command = "mkdir " + tempDataPath;
		        executeCommand(command, null);

		        command = "cp " + path + "/* " + tempDataPath + "/";
		        executeCommand(command, null);
		        
		        command="docker run --name " + currentServiceName + " -v " 
		        + tempDataPath + ":/data " + currentImageName + " " 
		        + currentArgs;

				executeCommand(command, outputTextArea);
		    }
		};

		monitorStatsRunnable = new Runnable() {
			float memStats[] = new float[5];
			float cpuStats[] = new float[5];

			@Override
			public void run() {
				int i = 0;
				while(true) {
					try {
						
						if(!runLocalThread.isAlive()) {
							return;
						}

						String command = "docker stats " + currentServiceName + " --no-stream --format '{{.CPUPerc}} {{.MemPerc}}'";
						String stats[] = executeCommand(command, outputTextArea).split(" ");

						cpuStats[i] = Float.parseFloat(stats[0].split("%")[0]);
						memStats[i] = Float.parseFloat(stats[1].split("%")[0]);

						if(i == 4) {
							float avg = 0;
							for(float cpuStat : cpuStats) {
								avg += cpuStat;
							}
							avg = avg/cpuStats.length;

							if(avg > threshold) {
								break;
							}

							avg = 0;
							for(float memStat : memStats) {
								avg += memStat;
							}
							avg = avg/memStats.length;

							if(avg > threshold) {
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

				if(runLocalThread.isAlive()) {
					
					String command="docker commit " + currentServiceName + " " + currentDockerNodeId + "-" + currentServiceName;
					executeCommand(command, null);

					command = "docker stop " + currentServiceName;
					executeCommand(command, null);

					command = "docker rm " + currentServiceName;
					executeCommand(command, null);

					currentServiceName = currentDockerNodeId + "-" + currentServiceName;

					command = "docker tag " + currentServiceName + " localhost:5000/" + currentServiceName;
					executeCommand(command, null);

					showProgressDialog("transferring container...");

					command = "docker push localhost:5000/" + currentServiceName;
					executeCommand(command, null);

					hideProgressDialog();
				}

				currentImageName = "localhost:5000/" + currentServiceName;
				runOnServerThread = new Thread(runOnServerRunnable);
				runOnServerThread.start();
			}
		};

		discoverServers();

		currentDockerNodeId  = executeCommand("docker node ls | grep \\*", null).split(" ")[0];
		System.out.println(currentDockerNodeId);

		pwd = executeCommand("pwd", null).split("\n")[0] + "/";

		jFrame = new JFrame("OffLoad");

		JLabel chooseImageLabel = new JLabel();
		chooseImageLabel.setBounds(50, 30, 400, 20);
		chooseImageLabel.setText("Choose Image");

    	comboBox = new JComboBox(imageLabels);
    	comboBox.setBounds(50, 50, 400, 50);
    	comboBox.addItemListener(new ItemListener() {
	        public void itemStateChanged(ItemEvent event) {
	            if (event.getStateChange() == ItemEvent.SELECTED) {
					imageChoice = comboBox.getSelectedIndex();
		       }
	        }
	    });

    	JLabel programFilePathLabel = new JLabel();
		programFilePathLabel.setBounds(50, 130, 200, 20);
		programFilePathLabel.setText("Enter file path");

    	filePathTextfield = new JTextField();
        filePathTextfield.setBounds(50, 150, 200, 50);

        JLabel argsLabel = new JLabel();
		argsLabel.setBounds(250, 130, 200, 20);
		argsLabel.setText("Enter arguments");

        argsTextfield = new JTextField();
        argsTextfield.setBounds(250, 150, 200, 50);

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

        stopContainerButton = new JButton("Stop Container");
        stopContainerButton.setBounds(50, 800, 200, 50);

        stopContainerButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e){
                        stopContainerButtonOnClick();
                    }
                });

        discoverServersButton = new JButton("Discover Servers");
        discoverServersButton.setBounds(250, 800, 200, 50);

        discoverServersButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e){
                        discoverServersButtonOnClick();
                    }
                });

        noOfServersLabel = new JLabel("Available Servers: 0");
        noOfServersLabel.setBounds(50, 880, 400, 20);
        noOfServersLabel.setText("Available Servers: " + workerNodeIds.length);

        jFrame.add(chooseImageLabel);
	    jFrame.add(comboBox);
	    jFrame.add(programFilePathLabel);
	    jFrame.add(filePathTextfield);
	    jFrame.add(argsLabel);
	    jFrame.add(argsTextfield);
	    jFrame.add(manualButtonsLabel);
	    jFrame.add(startOnLocalButton);
	    jFrame.add(startOnServerButton);
	    jFrame.add(automaticButtonsLabel);
	    jFrame.add(startAutomaticButton);
	    jFrame.add(outputLabel);
	    jFrame.add(scrollPane);
	    jFrame.add(stopContainerButton);
	    jFrame.add(discoverServersButton);
	    jFrame.add(noOfServersLabel);

	    jFrame.setLayout(null);
	    jFrame.setSize(500,1000);

	    progressDialog = new JDialog(jFrame , "Please Wait...", false);
	    progressDialog.setSize(400,200);
	    progressDialog.setResizable(false);
	    progressDialog.setBounds(50, 300, 400, 200);
	    progressDialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

	    progressDialogLabel = new JLabel();
	    progressDialogLabel.setBounds(100, 50, 300, 100);
	    progressDialog.add(progressDialogLabel);

	    alertDialog = new JDialog(jFrame , "Alert!", false);
	    alertDialog.setSize(400,200);
	    alertDialog.setResizable(false);
	    alertDialog.setBounds(50, 300, 400, 200);

	    alertDialogLabel = new JLabel();
	    alertDialogLabel.setBounds(100, 50, 300, 20);
	    alertDialog.add(alertDialogLabel);

	    alertDialogDismissButton = new JButton("Dismiss");
	    alertDialogDismissButton.setBounds(100, 100, 50, 50);
		//alertDialog.add(alertDialogDismissButton);

		alertDialogDismissButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e){
                        alertDialog.setVisible(false);
                    }
                });
    }
	
	void discoverServers() {
		String temp[] = executeCommand("docker node ls -f \"role=worker\" | grep Ready", null).split("\n");
		System.out.println(temp.length);
		if(temp.length == 1) {
			if(temp[0].equals("")) {
				workerNodeIds = new String[0];
				return;
			}
		}
		
		workerNodeIds = new String[temp.length];
		for(int i = 0; i<temp.length ; i++) {
			System.out.println(temp[i].split(" ")[0]);
			workerNodeIds[i] = temp[i].split(" ")[0];
		}
	}

	void disableControls() {
		comboBox.setEnabled(false);
		filePathTextfield.setEnabled(false);
		startOnLocalButton.setEnabled(false);
		startOnServerButton.setEnabled(false);
		startAutomaticButton.setEnabled(false);
	}

	void enableControls() {
		comboBox.setEnabled(true);
		filePathTextfield.setEnabled(true);
		startOnLocalButton.setEnabled(true);
		startOnServerButton.setEnabled(true);
		startAutomaticButton.setEnabled(true);
	}

	void showProgressDialog(String message) {
		progressDialogLabel.setText(message);
		progressDialog.setVisible(true);
	}

	void hideProgressDialog() {
		progressDialog.setVisible(false);
	}

	void showAlertDialog(String message) {
		alertDialogLabel.setText(message);
		alertDialog.setVisible(true);
	}

	void hideAlertDialog() {
		alertDialog.setVisible(false);
	}

	void runLocalButtonOnClick() {
		disableControls();
		path = filePathTextfield.getText().toString();
		currentServiceName = serviceNames[imageChoice] + "-" + System.currentTimeMillis();
		currentImageName = imageNames[imageChoice];
		currentArgs = argsTextfield.getText().toString();

		runLocalThread = new Thread(runLocalRunnable);
		runLocalThread.start();
	}

	void runServerButtonOnClick() {
		if(workerNodeIds.length == 0) {
			showAlertDialog("No Servers Available");
			return;
		}

		disableControls();
		path = filePathTextfield.getText().toString();
		currentServiceName = serviceNames[imageChoice] + "-" + System.currentTimeMillis();
		currentImageName = imageNames[imageChoice];
		currentArgs = argsTextfield.getText().toString();

		runOnServerThread = new Thread(runOnServerRunnable);
		runOnServerThread.start();
	}

	void runAutomaticButtonOnClick() {
		if(workerNodeIds.length == 0) {
			showAlertDialog("No Servers Available");
			return;
		}

		disableControls();
		path = filePathTextfield.getText().toString();
		currentServiceName = serviceNames[imageChoice] + "-" + System.currentTimeMillis();
		currentImageName = imageNames[imageChoice];
		currentArgs = argsTextfield.getText().toString();

		runLocalThread = new Thread(runLocalRunnable);
		runLocalThread.start();

		monitorStatsThread = new Thread(monitorStatsRunnable);
		monitorStatsThread.start();
	}

	void stopContainerButtonOnClick() {
		if (currentServiceName != null) {
			String command = "docker stop " + currentServiceName;
			executeCommand(command, null);

			command = "docker rm " + currentServiceName;
			executeCommand(command, null);

			command = "docker service rm " + currentServiceName;
			executeCommand(command, null);
		}

		enableControls();
	}

	void discoverServersButtonOnClick() {
		discoverServers();
		noOfServersLabel.setText("Available Servers: " + workerNodeIds.length);
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
