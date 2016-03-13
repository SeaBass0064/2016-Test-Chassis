package org.usfirst.frc.team4028.robot;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.TimeZone;

import org.usfirst.frc.team4028.robot.Constants.RobotMap;
import org.usfirst.frc.team4028.robot.RobotData.InputData;
import org.usfirst.frc.team4028.robot.RobotData.OutputData;
import org.usfirst.frc.team4028.robot.RobotData.WorkingData;

import edu.wpi.first.wpilibj.CANTalon;
import edu.wpi.first.wpilibj.CANTalon.FeedbackDevice;
import edu.wpi.first.wpilibj.CameraServer;
import edu.wpi.first.wpilibj.DigitalInput;
import edu.wpi.first.wpilibj.DoubleSolenoid;
import edu.wpi.first.wpilibj.DoubleSolenoid.Value;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.Encoder;
import edu.wpi.first.wpilibj.IterativeRobot;
import edu.wpi.first.wpilibj.Joystick;
import edu.wpi.first.wpilibj.PIDController;
import edu.wpi.first.wpilibj.PIDSource;
import edu.wpi.first.wpilibj.PIDSourceType;
import edu.wpi.first.wpilibj.PowerDistributionPanel;
import edu.wpi.first.wpilibj.SerialPort;
import edu.wpi.first.wpilibj.RobotDrive;
import edu.wpi.first.wpilibj.Utility;
import edu.wpi.first.wpilibj.VictorSP;
import edu.wpi.first.wpilibj.livewindow.LiveWindow;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj.SPI;
import edu.wpi.first.wpilibj.I2C;

import com.kauailabs.navx.frc.AHRS;

/**
 * Date			Rev		Author						Comments
 * -----------	------	-------------------------	----------------------------------
 * 18.Feb.2-16 	0.81	Sebastian Rodriguez			Updated controller to run infeed and drive off of one motor, added all shooter motors
 * 15.Feb.2016	0.8		Sebastian Rodriguez			Added more untested auton base code, refined pid control for turret
 * 8.Feb.2016	0.7		Sebastian Rodriguez			PID control for the turret, untested auton mode
 * 6.Feb.2016	0.6		Sebastian Rodriguez			Added Infeed code
 * 4.Feb.2016	0.5		Sebastian Rodriguez			Added Turret and turret encoder
 * 28.Jan.2016	0.4		Sebastian Rodriguez			Added untested navx code
 * 23.Jan.2016	0.3		Sebastian Rodriguez			Changed to 6 wheel drive train
 * 18.Jan.2016	0.21	Sebastian Rodriguez			Debugging to allow for Solenoid functionality
 * 16.Jan.2016 	0.2		Sebastian Rodriguez			Added Untested Solenoid functionality
 * 													Setup two additional Talons and two Victors
 * 15.Jan.2016	0.1		Sebastian Rodriguez			Initial Version
 * 													-Basic Robot Config
 * 													-Added Arcade Drive functionality
 *
 */



public class Robot extends IterativeRobot
{
	// ===========================================================
	//   Define class level instance variables for Robot Runtime controllable objects  
	// ===========================================================
	
	// Driver & Operator station gamepads
	private Joystick _driverGamepad;
	private Joystick _operatorGamepad;

	// CIM DC Motors on Talon SRX Speed Controllers (via CAN Bus)
	private CANTalon _leftDriveMasterMtr;
	private CANTalon _leftDriveSlaveMtr;
	private CANTalon _leftDriveSlave2Mtr;
	private CANTalon _rightDriveMasterMtr;
	private CANTalon _rightDriveSlaveMtr;
	private CANTalon _rightDriveSlave2Mtr;
	private CANTalon _turretMtr;
	private CANTalon _shooterMasterMtr;
	private CANTalon _shooterSlaveMtr;
	private CANTalon _sliderMtr;
	private CANTalon _infeedTiltMtr;
	
	// CIM DC Motors on Victor SP Speed Controllers (via PWM Ports)
	private VictorSP _infeedAcqMtr;
	private VictorSP _kickerMtr;
	
	// Limit switches for turret
	private DigitalInput _turretHomeLimitSwitch;
	private DigitalInput _turretApproachingHomeLimitSwitch;
	
	// Arcade Drive with four drive motors
	private RobotDrive _robotDrive;
	
	// Power Distribution Panel
	private PowerDistributionPanel _pdp;
	
	// Pneumatic Solenoids for Air Cylinders
	private DoubleSolenoid _pumaFrontSolenoid;
	private DoubleSolenoid _pumaBackSolenoid;
	private DoubleSolenoid _shifterSolenoid;
	
	// Camera
	CameraServer server;
	
	// navX
	private AHRS _navXSensor;
	
	// Socket for Vision 
	private Socket _visionServer;
	
	// ==================
	// PID Controller
	// ==================
	//private PIDController _turretControl;
	//private Encoder _turretEncoder;
	
	// ===========================================================
	//   Define class level working variables 
	// ===========================================================
		
	// DTO (Data Transfer Object) holding all live Robot Data Values
	RobotData _robotLiveData;

	// Wrapper around data logging (if it is enabled)
	DataLogger _dataLogger;
	
	// Smart Dashboard chooser
	SendableChooser autonModeChooser;
	
	boolean _isTurretAxisZeroedYet = false;
	boolean _isInfeedTiltAxisZeroedYet = false;
	boolean _isSliderAxisZeroedYet = false;
	
    /*****************************************************************************************************
     * This function is run when the robot is first started up.
	 * This is where we initialize the robot hardware configuration.
	 * 
	 * We try and fully configure the Motor controllers each robot startup.
	 *  We are as explicit as possible even when we do not need to be to make it as clear as possible for others
	 * 	This way we do not assume what their current configuration is.
	 * 	The only thing we depend on is that the CAN Bus address is correct
	 * 
	 * FYI:	Additional relevant documentation about each control object is included here
     *****************************************************************************************************/
    public void robotInit() 
    {    	
    	
    	// ===================
    	// Left Drive Motors, Tandem Pair, looking out motor shaft: CW = Drive FWD
    	// ===================
    	_leftDriveMasterMtr = new CANTalon(RobotMap.CAN_ADDR_LEFT_DRIVE_MASTER_TALON);
    	_leftDriveMasterMtr.changeControlMode(CANTalon.TalonControlMode.PercentVbus);	// open loop throttle
    	_leftDriveMasterMtr.enableBrakeMode(false);							// default to brake mode DISABLED
    	//_leftDriveMasterMtr.setFeedbackDevice(FeedbackDevice.QuadEncoder);	// set encoder to be feedback device
    	_leftDriveMasterMtr.reverseSensor(false);  							// do not invert encoder feedback
    
    	_leftDriveSlaveMtr = new CANTalon(RobotMap.CAN_ADDR_LEFT_DRIVE_SLAVE_TALON);	
    	_leftDriveSlaveMtr.changeControlMode(CANTalon.TalonControlMode.Follower);	// set this mtr ctrlr as a slave
    	_leftDriveSlaveMtr.set(RobotMap.CAN_ADDR_LEFT_DRIVE_MASTER_TALON);
    	_leftDriveSlaveMtr.enableBrakeMode(false);							// default to brake mode DISABLED
    	
    	_leftDriveSlave2Mtr = new CANTalon(RobotMap.CAN_ADDR_LEFT_DRIVE_SLAVE_2_TALON);
    	_leftDriveSlave2Mtr.changeControlMode(CANTalon.TalonControlMode.Follower);   // set this mtr ctrlr as a slave
    	_leftDriveSlave2Mtr.set(RobotMap.CAN_ADDR_LEFT_DRIVE_MASTER_TALON);
    	_leftDriveSlave2Mtr.enableBrakeMode(false);
    	
    	// ===================
    	// Right Drive Motors, Tandem Pair, looking out motor shaft: CCW = Drive FWD
    	// ===================
    	_rightDriveMasterMtr = new CANTalon(RobotMap.CAN_ADDR_RIGHT_DRIVE_MASTER_TALON);
    	_rightDriveMasterMtr.changeControlMode(CANTalon.TalonControlMode.PercentVbus);	// open loop throttle.
    	_rightDriveMasterMtr.enableBrakeMode(false);						// default to brake mode DISABLED
    	//_rightDriveMasterMtr.setFeedbackDevice(FeedbackDevice.QuadEncoder);	// set encoder to be feedback device
    	_rightDriveMasterMtr.reverseSensor(true);  							// invert encoder feedback
    	
    	_rightDriveSlaveMtr = new CANTalon(RobotMap.CAN_ADDR_RIGHT_DRIVE_SLAVE_TALON);	
    	_rightDriveSlaveMtr.changeControlMode(CANTalon.TalonControlMode.Follower);	// set this mtr ctrlr as a slave
    	_rightDriveSlaveMtr.set(RobotMap.CAN_ADDR_RIGHT_DRIVE_MASTER_TALON);
    	_rightDriveSlaveMtr.enableBrakeMode(false);							// default to brake mode DISABLED
    	
    	_rightDriveSlave2Mtr = new CANTalon(RobotMap.CAN_ADDR_RIGHT_DRIVE_SLAVE_2_TALON);
    	_rightDriveSlave2Mtr.changeControlMode(CANTalon.TalonControlMode.Follower);	// set this mtr ctrlr as a slave
    	_rightDriveSlave2Mtr.set(RobotMap.CAN_ADDR_RIGHT_DRIVE_MASTER_TALON);
    	_rightDriveSlave2Mtr.enableBrakeMode(false);
    	
    	// ===================
    	// Infeed Tilt
    	// ===================
    	_infeedTiltMtr = new CANTalon(RobotMap.CAN_ADDR_INFEED_TILT_MTR_TALON);
    	_infeedTiltMtr.setFeedbackDevice(FeedbackDevice.CtreMagEncoder_Relative);
    	_infeedTiltMtr.changeControlMode(CANTalon.TalonControlMode.PercentVbus);	// we start in % vbus mode until we zero then we swap to position mode
    	_infeedTiltMtr.enableBrakeMode(true);
    	_infeedTiltMtr.enableLimitSwitch(true, false);		// we are using the FWD limit switch
    	_infeedTiltMtr.reverseSensor(false);
    	_infeedTiltMtr.ConfigFwdLimitSwitchNormallyOpen(false);
    	
    	// ===================
    	// Infeed Acquisition
    	// ===================
    	_infeedAcqMtr = new VictorSP(RobotMap.INFEED_ACQ_MTR_PWM_PORT);
    	
    	// ===================
    	// Turret
    	// ===================
    	_turretMtr = new CANTalon(RobotMap.CAN_ADDR_TURRET_TALON);
    	_turretMtr.setFeedbackDevice(FeedbackDevice.CtreMagEncoder_Relative);
    	_turretMtr.changeControlMode(CANTalon.TalonControlMode.PercentVbus);
    	_turretMtr.reverseSensor(false);
    	_turretMtr.enableBrakeMode(true);
    	_turretMtr.enableLimitSwitch(false, false);
    	_turretMtr.configNominalOutputVoltage(+0.0f, -0.0f);
    	_turretMtr.configPeakOutputVoltage(+6.0f, -6.0f);
    	
    	// ===================
    	// Shooter
    	// ===================
    	_shooterMasterMtr = new CANTalon(RobotMap.CAN_ADDR_MASTER_SHOOTER_TALON);
    	_shooterMasterMtr.setFeedbackDevice(FeedbackDevice.QuadEncoder);
    	_shooterMasterMtr.reverseSensor(true);
    	_shooterMasterMtr.enableBrakeMode(false);
    	_shooterMasterMtr.configNominalOutputVoltage(+0.0f, -0.0f);
    	_shooterMasterMtr.configPeakOutputVoltage(+12.0f, 0.0f);
    	_shooterMasterMtr.changeControlMode(CANTalon.TalonControlMode.Speed);
    	//_shooterMasterMtr.changeControlMode(CANTalon.TalonControlMode.PercentVbus);  // 
    	_shooterMasterMtr.configEncoderCodesPerRev(RobotMap.SHOOTER_ENCODER_COUNTS_PER_REV);	// try to enable Unit Scaling
    	
    	// setup shooter PID Loop
    	// shooterMaxVelociytInNativeUnitsPer100mSec = CalcShooterVelociytInNativeUnitsPer100mSec(RobotMap.SHOOTER_MAX_MOTOR_RPM);
    	//double shooterFeedFwdGain = CalcShooterFeedFwdGain(shooterMaxVelociytInNativeUnitsPer100mSec);
    	_shooterMasterMtr.setPID(RobotMap.SHOOTER_KP, RobotMap.SHOOTER_KI, RobotMap.SHOOTER_KD, RobotMap.SHOOTER_KF, RobotMap.SHOOTER_IZONE, RobotMap.SHOOTER_RAMPRATE, RobotMap.SHOOTER_PROFILE);
    	_shooterMasterMtr.setProfile(RobotMap.SHOOTER_PROFILE);
    	
    	_shooterSlaveMtr = new CANTalon(RobotMap.CAN_ADDR_SLAVE_SHOOTER_TALON);
    	_shooterSlaveMtr.changeControlMode(CANTalon.TalonControlMode.Follower);
    	_shooterSlaveMtr.set(RobotMap.CAN_ADDR_MASTER_SHOOTER_TALON);
    	_shooterSlaveMtr.enableBrakeMode(false);
    	
    	// ===================
    	// Kicker
    	// ===================
    	_kickerMtr = new VictorSP(RobotMap.SHOOTER_KICKER_PWM_PORT);
    	
    	// ===================
    	// Slider
    	// ===================
    	_sliderMtr = new CANTalon(RobotMap.CAN_ADDR_SHOOTER_SLIDER_TALON);
    	_sliderMtr.changeControlMode(CANTalon.TalonControlMode.PercentVbus);			// default to %VBus on startup, chgs to Position in Axis Zero Function
    	_sliderMtr.setFeedbackDevice(FeedbackDevice.QuadEncoder);
    	_sliderMtr.enableLimitSwitch(false, true);
    	_sliderMtr.ConfigRevLimitSwitchNormallyOpen(false);
    	_sliderMtr.reverseSensor(true);
    	_sliderMtr.enableBrakeMode(true);
    	_sliderMtr.configNominalOutputVoltage(+0.0f, -0.0f);
    	_sliderMtr.configPeakOutputVoltage(+12.0f, -12.0f);
    	_sliderMtr.configEncoderCodesPerRev(RobotMap.SLIDER_ENCODER_COUNTS_PER_REV);	// try to enable Unit Scaling
    	    	
    	// ===================
    	// Limit Switches
    	// ===================
    	_turretHomeLimitSwitch = new DigitalInput(RobotMap.TURRET_HOME_LIMIT_SWITCH_DIO_PORT);
    	_turretApproachingHomeLimitSwitch = new DigitalInput(RobotMap.TURRET_APPROACHING_HOME_LIMIT_SWITCH_DIO_PORT);
    	
    	// ===================
    	// Gamepads
    	// ===================
    	_driverGamepad = new Joystick(RobotMap.DRIVER_GAMEPAD_USB_PORT);				// std Logitech F310 Gamepad  
    	_operatorGamepad = new Joystick(RobotMap.OPERATOR_GAMEPAD_USB_PORT);			// std Logitech F310 Gamepad  
    	
    	// ===================
    	// Arcade Drive
    	//====================
    	// Arcade Drive configured to drive in three motor setup, other two motors follow as slaves 
    	_robotDrive = new RobotDrive(_leftDriveMasterMtr,_rightDriveMasterMtr);
    	   	
    	// ==================
    	// Power Distribution Panel
    	// ==================
    	_pdp = new PowerDistributionPanel();
    	
    	//===================
    	// Solenoids
    	//===================
    	_pumaFrontSolenoid = new DoubleSolenoid(RobotMap.CAN_ADDR_PCM, RobotMap.PCM_PORT_PUMA_FRONT_SOLENOID_EXTEND, RobotMap.PCM_PORT_PUMA_FRONT_SOLENOID_RETRACT);
    	_pumaBackSolenoid = new DoubleSolenoid(RobotMap.CAN_ADDR_PCM, RobotMap.PCM_PORT_PUMA_BACK_SOLENOID_EXTEND, RobotMap.PCM_PORT_PUMA_BACK_SOLENOID_RETRACT);
    	_shifterSolenoid = new DoubleSolenoid(RobotMap.CAN_ADDR_PCM, RobotMap.PCM_PORT_SHIFTER_SOLENOID_EXTEND, RobotMap.PCM_PORT_SHIFTER_SOLENOID_RETRACT);
    	
    	//===================
    	// Default all Absolute Position Axes to NOT ZEROED
    	//===================
    	_isTurretAxisZeroedYet = false;
    	_isInfeedTiltAxisZeroedYet = false;
    	_isSliderAxisZeroedYet = false;
    	
    	//===================
    	// PIDController
    	//===================
    	// Test code for PID loop that runs on the Roborio, haven't figured out how to get PIDSourceType to provide an input values, not sure if it will be necessary though since we can run PID loops on the talons
    	/*
    	_turretEncoder = new Encoder(0,0,1);
    	_turretEncoder.setPIDSourceType(PIDSourceType.kDisplacement);
    	_turretControl= new PIDController(1.0, 0.0, 0.0, _turretEncoder, _turret);
    	*/
    	
    	//===================
    	// Camera
    	//===================
        server = CameraServer.getInstance();
        server.setQuality(25);
        //the camera name (ex "cam0") can be found through the roborio web interface
        server.startAutomaticCapture("cam0");
        // note since the camera is initialized here, you cannot move it to another USB socket after the robot is powered up
        
        //===================
        // Smart DashBoard User Input
        //===================
        autonModeChooser = new SendableChooser();
        autonModeChooser.addDefault("Do Nothing", RobotData.AutonMode.DO_NOTHING);
        autonModeChooser.addObject("Test", RobotData.AutonMode.TEST);
        SmartDashboard.putData("Autonomous mode chooser", autonModeChooser);
    	
        //===================
    	// write jar (build) date & time to the dashboard
        //===================
    	try
    	{
    		//DriverStation.reportError("** Team 4028 The Beak Squad **", false);
    		
    		//get the path of the currently executing jar file
			String currentJarFilePath = Robot.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();		
			//DriverStation.reportError(currentJarFilePath , false);
			Path filePath = Paths.get(currentJarFilePath);
			
			//get file system details from current file
			BasicFileAttributes attr = Files.readAttributes(filePath, BasicFileAttributes.class);
			Date utcFileDate = new Date(attr.lastModifiedTime().toMillis());
	
			// convert from UTC to local time zone
			SimpleDateFormat outputFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			outputFormatter.setTimeZone(TimeZone.getTimeZone("US/Eastern")); 
			String newDateString = outputFormatter.format(utcFileDate);
			
			// write the build date & time to the operator's console log window
			DriverStation.reportError("Build Date and Time: " + newDateString, false);
			
		} 
    	catch (URISyntaxException e) 
    	{
    		DriverStation.reportError("Error determining filename of current JAR file", true);
			//e.printStackTrace();
		} 
    	catch (IOException e) 
    	{	
    		DriverStation.reportError("General Error trying to determine current JAR file", true);
			//e.printStackTrace();
		}
    	
    	//===================
    	// try to communicate to the naxX
    	//===================
    	try 
    	{
            /* Communicate w/ navX MXP via one of the following ports                           */
            /*   				I2C.Port.kMXP, 												   	*/
            /* 					SerialPort.Port.kMXP, 										   	*/
            /* 					SerialPort.Port.kUSB										   	*/
            /* 					SPI.Port.kMXP   			plugged into mxp port on RoboRio	*/			
            /* See http://navx-mxp.kauailabs.com/guidance/selecting-an-interface/ for details.  */
            _navXSensor = new AHRS(SPI.Port.kMXP);
            
            DriverStation.reportError("..navX sensor connected" , false);
        } 
    	catch (RuntimeException ex ) 
    	{
            DriverStation.reportError("Error instantiating navX MXP:  " + ex.getMessage(), true);
        }
    	
    }

    // ========================================================================
    //
    // This method is called once at the start of Antonomous Mode
    //
    // ========================================================================
    public void autonomousInit() {
    	_robotLiveData = new RobotData();
    	//get local references to make variable references shorter
    	InputData inputDataValues = _robotLiveData.InputDataValues;
    	WorkingData workingDataValues = _robotLiveData.WorkingDataValues;
    	OutputData outputDataValues = _robotLiveData.OutputDataValues;
    	
    	// Set desired initial (default) solenoid positions
    	outputDataValues.PumaFrontSolenoidPosition = RobotMap.PUMA_FRONT_SOLENOID_CLOSED_POSITION;
    	outputDataValues.PumaBackSolenoidPosition = RobotMap.PUMA_BACK_SOLENOID_OPEN_POSITION;
    	outputDataValues.ShifterSolenoidPosition = RobotMap.SHIFTER_SOLENOID_OPEN_POSITION;
    	
    	// initialize axis (Encoder) positions
    	_leftDriveMasterMtr.setPosition(0);
    	_rightDriveMasterMtr.setPosition(0);
    	_turretMtr.setPosition(0);
    	
    	// get initial values from Encoders
    	workingDataValues.LeftDriveEncoderInitialCount = _leftDriveMasterMtr.getPosition();
    	workingDataValues.RightDriveEncoderInitialCount = _rightDriveMasterMtr.getPosition();
    	workingDataValues.TurretEncoderInitialCount = _turretMtr.getPosition();
    	
    	// get user input values from the Smart Dashboard
    	inputDataValues.AutonModeRequested = (RobotData.AutonMode) autonModeChooser.getSelected();
    	
    	if (!_isTurretAxisZeroedYet)
    	{
    		ZeroTurretAxis(_robotLiveData);
    	}
    	
    	if (!_isInfeedTiltAxisZeroedYet)
    	{
    		ZeroInfeedTiltAxis(_robotLiveData);
    	}
    	
    	if (!_isSliderAxisZeroedYet)
    	{
    		ZeroSliderAxis(_robotLiveData);
    	}
    	
    	// Optionally Setup logging to a usb stick
    	setupLogging("auton");
    }

	// ========================================================================
    // This method is called every loop (about 50 x / sec) or 1 x every 20 mSec
    // ========================================================================
    public void autonomousPeriodic() {	
    	//get local references to make variable references shorter
    	InputData inputDataValues = _robotLiveData.InputDataValues;
    	WorkingData workingDataValues = _robotLiveData.WorkingDataValues;
    	OutputData outputDataValues = _robotLiveData.OutputDataValues;
    	
    	// ===============================
    	// Step 1: Get Inputs
    	// ===============================
    	UpdateInputAndCalcWorkingDataValues(inputDataValues, workingDataValues);
    	
    	outputDataValues.DriversStationMsg = "";
    	
    	// ===============================
    	// Step 2: call the appropriate auton mode
    	// ===============================
    	
    	switch(inputDataValues.AutonModeRequested)
    	{
    	     case DO_NOTHING:
    	     	 autonomousUndefined();
    	     	 break;
    	     
    	     case TEST:
    	    	 autonomousTest();
    	    	 break;
			case UNDEFINED:
				break;
			default:
				break;
    	}
    	
    	// ===============================
    	// Step 3: Set outputs
    	// ===============================
    	
    	// only set the motor values in a real auton mode
    	if (inputDataValues.AutonModeRequested != RobotData.AutonMode.DO_NOTHING)
    	{
    		_leftDriveMasterMtr.set(outputDataValues.ArcadeDriveThrottleAdjCmd);
        	_rightDriveMasterMtr.set(outputDataValues.ArcadeDriveTurnAdjCmd);
        	
        	_turretMtr.set(outputDataValues.TurretTargetPositionCmd);
        	
        	_infeedAcqMtr.set(outputDataValues.InfeedAcqMtrVelocityCmd);
        	_infeedTiltMtr.set(outputDataValues.InfeedTiltMtrVelocityCmd);
        	
        	_kickerMtr.set(outputDataValues.KickerMtrVelocityCmd);
        	_shooterMasterMtr.set(outputDataValues.ShooterMtrVelocityCmd);
        	_sliderMtr.set(outputDataValues.SliderTargetPositionCmd);
        	
        	_pumaFrontSolenoid.set(outputDataValues.PumaFrontSolenoidPosition);
        	_pumaBackSolenoid.set(outputDataValues.PumaBackSolenoidPosition);
        	_shifterSolenoid.set(outputDataValues.ShifterSolenoidPosition);
    	}
    	
    	// ==============================
    	// Step 4: Update the Dashboard
    	// ==============================
    	UpdateDashboard(_robotLiveData);
    	
    	// set last scan DT
    	workingDataValues.LastScanDT = new Date();
    	
    	// optionally send messages to the driver station
    	if ((outputDataValues.DriversStationMsg != null) && (outputDataValues.DriversStationMsg.length() > 0))
    	{
    		DriverStation.reportError(outputDataValues.DriversStationMsg, false);
    	}
    	
    	// =============================
    	// 5.0 Optional Data Logging
    	// =============================
    	
    	if(workingDataValues.IsLoggingEnabled == true)
    	{
    		_dataLogger.WriteData(_robotLiveData);
    	}
    	
    }
    
    public void autonomousUndefined()
    {
    }
    
    public void autonomousDoNothing()
    {
    }
    
    public void autonomousTest()
    {
    }
    
    /*
     *****************************************************************************************************
     * This function is called 1x each time the robot enters tele-operated mode
     *  (setup the initial robot state for telop mode)
     *****************************************************************************************************
     */
    public void teleopInit()
    {
    	_robotLiveData = new RobotData();
    	//get local references to make variable references shorter
    	InputData inputDataValues = _robotLiveData.InputDataValues;
    	WorkingData workingDataValues = _robotLiveData.WorkingDataValues;
    	OutputData outputDataValues = _robotLiveData.OutputDataValues;
    	
    	// set motors to 0 position/velocity command 
    	outputDataValues.ArcadeDriveThrottleAdjCmd = 0.0;
    	outputDataValues.ArcadeDriveTurnAdjCmd = 0.0;
    	outputDataValues.InfeedAcqMtrVelocityCmd = 0.0;
    	outputDataValues.InfeedTiltMtrVelocityCmd = 0.0;
    	    	
    	outputDataValues.KickerMtrVelocityCmd = 0.0;
    	outputDataValues.ShooterMtrVelocityCmd = 0.0;
    	outputDataValues.SliderVelocityCmd = 0.0;
    	    	
    	// initialize axis (Encoder) positions (for any talon where we care about position but do not have a home position
    	_leftDriveMasterMtr.setPosition(0);
    	_rightDriveMasterMtr.setPosition(0);
    	
    	// set motors to output velocity command 
    	_leftDriveMasterMtr.set(outputDataValues.ArcadeDriveThrottleAdjCmd);
    	_rightDriveMasterMtr.set(outputDataValues.ArcadeDriveTurnAdjCmd);
    	_infeedAcqMtr.set(outputDataValues.InfeedAcqMtrVelocityCmd);
    	_shooterMasterMtr.set(outputDataValues.ShooterMtrVelocityCmd);
    	_kickerMtr.set(outputDataValues.KickerMtrVelocityCmd);
    	//_sliderMtr.set(outputDataValues.SliderVelocityCmd);
    	  	    	
    	// init the drive speed scaling factor to 95%
    	workingDataValues.DriveSpeedScalingFactor = 0.95;

    	// get initial values from Encoders
    	workingDataValues.LeftDriveEncoderInitialCount = _leftDriveMasterMtr.getPosition();
    	workingDataValues.RightDriveEncoderInitialCount = _rightDriveMasterMtr.getPosition();

    	// set our desired default state for the test solenoids
    	outputDataValues.PumaFrontSolenoidPosition = RobotMap.PUMA_FRONT_SOLENOID_CLOSED_POSITION;
    	outputDataValues.PumaBackSolenoidPosition = RobotMap.PUMA_BACK_SOLENOID_CLOSED_POSITION;
    	outputDataValues.ShifterSolenoidPosition = RobotMap.SHIFTER_SOLENOID_OPEN_POSITION;
    	
    	// set initial state of "pressed last scan" working values to be false
    	workingDataValues.IsPumaFrontToggleBtnPressedLastScan = false;
    	workingDataValues.IsPumaBackToggleBtnPressedLastScan = false;
    	workingDataValues.IsShifterToggleBtnPressedLastScan = false;
    	
    	// set the initial states to solenoids
    	_pumaFrontSolenoid.set(outputDataValues.PumaFrontSolenoidPosition);
    	_pumaBackSolenoid.set(outputDataValues.PumaBackSolenoidPosition);
    	_shifterSolenoid.set(outputDataValues.ShifterSolenoidPosition);
    	
    	inputDataValues.IsInfeedAcquireBtnPressed = false;
    	inputDataValues.IsInfeedReleaseBtnPressed = false;
    	workingDataValues.IsTurretEncoderDegreesTargetYet = false;
    	
    	if (!_isTurretAxisZeroedYet)
    	{
    		ZeroTurretAxis(_robotLiveData);
    	}
    	else
    	{
    		outputDataValues.TurretTargetPositionCmd = _turretMtr.getPosition();
    	}
    	
    	if (!_isInfeedTiltAxisZeroedYet)
    	{
    		ZeroInfeedTiltAxis(_robotLiveData);
    	}
    	else 
    	{
    		outputDataValues.InfeedTiltTargetPositionInRotationsCmd = RobotMap.INFEED_TILT_STORED_POSITION_CMD;
    	}
    	
    	if (!_isSliderAxisZeroedYet)
    	{
    		ZeroSliderAxis(_robotLiveData);
    	}
    	else
    	{
    		outputDataValues.SliderTargetPositionCmd = RobotMap.SLIDER_DEFAULT_TARGET_POSITION;
    	}
    	
    	// ===================
    	// optionally setup logging to USB Stick (if it is plugged into one of the RoboRio Host USB ports)
    	// ===================
    	setupLogging("telop");
    	
    }
    	
    /*
     *****************************************************************************************************
     * This function is called periodically during operator control
     * 	(about 50x/sec or about every 20 mSec)
     * 
     * Four (4) main steps
     * 	1.	Get Inputs
     *  2. 	Update Working Values
     *  3.	Calc new motor speeds
     *  4.	Set Outputs
     *  5.	Update Dashboard
     * 
     *****************************************************************************************************
     */
    public void teleopPeriodic() 
    {
    	// =====================================
    	// === Step 0: get local references for objects that are properties of _robotLiveData to
    	//				make variable references shorter
    	// =====================================
    	InputData inputDataValues = _robotLiveData.InputDataValues;
    	WorkingData workingDataValues = _robotLiveData.WorkingDataValues;
    	OutputData outputDataValues = _robotLiveData.OutputDataValues;
        	
    	// =====================================
    	// Step 1: Get Inputs  and Update Working Values
    	// =====================================
    	UpdateInputAndCalcWorkingDataValues(inputDataValues, workingDataValues);
    	outputDataValues.DriversStationMsg = "";    
    	
    	// =====================================
    	// === Step 2.1: Calc New Drive Motor Speeds ===
    	// =====================================
    	// set the drive speed scale factor (currently we support 0.7 & 1.0)
    	// 	notes: 	this is a toggle,  the previous value is retained between scans
    	//			need to de-bounce key press since the scan rate is so fast 
    	if(inputDataValues.IsScaleDriveSpeedUpBtnPressed 
    			&& inputDataValues.IsScaleDriveSpeedDownBtnPressed)
    	{
    		// Don't change scale factor if both buttons are pressed
    	}
    	else if(inputDataValues.IsScaleDriveSpeedUpBtnPressed 
    			&& !inputDataValues.IsScaleDriveSpeedDownBtnPressed)
    	{
    		// scale up
    		workingDataValues.DriveSpeedScalingFactor = 1;
    	}
    	else if(!inputDataValues.IsScaleDriveSpeedUpBtnPressed
    			&& inputDataValues.IsScaleDriveSpeedDownBtnPressed)
    	{
    		// scale down
    		workingDataValues.DriveSpeedScalingFactor = 0.8;
    	}
    	else if(!inputDataValues.IsScaleDriveSpeedUpBtnPressed 
    			&& !inputDataValues.IsScaleDriveSpeedDownBtnPressed)
    	{
    		// if neither button is pressed do nothing
    	}
    	
    	outputDataValues.ArcadeDriveThrottleAdjCmd 
    			= inputDataValues.ArcadeDriveThrottleRawCmd * workingDataValues.DriveSpeedScalingFactor;  	
    	outputDataValues.ArcadeDriveTurnAdjCmd 
    			= inputDataValues.ArcadeDriveTurnRawCmd * workingDataValues.DriveSpeedScalingFactor * 0.6;

    	// =====================================
    	// Step 2.2:  Infeed Tilt 
    	// =====================================
    	// Tilt the infeed up and down
    	
    	if (_infeedTiltMtr.getControlMode() == CANTalon.TalonControlMode.Position)
    	{
    		if (inputDataValues.IsInfeedTiltDeployBtnPressed && !inputDataValues.IsInfeedTiltStoreBtnPressed)
    		{
    			// rotate down;
    			outputDataValues.InfeedTiltTargetPositionInRotationsCmd = RobotMap.INFEED_TILT_DEPLOYED_POSITION_CMD;
    		}
    		else if (!inputDataValues.IsInfeedTiltDeployBtnPressed && inputDataValues.IsInfeedTiltStoreBtnPressed)
    		{
    			// rotate up
    			outputDataValues.InfeedTiltTargetPositionInRotationsCmd = RobotMap.INFEED_TILT_STORED_POSITION_CMD;
    		}
    		else if (!inputDataValues.IsInfeedTiltDeployBtnPressed && !inputDataValues.IsInfeedTiltStoreBtnPressed)
    		{
    			if (inputDataValues.InfeedRawTiltCmd < -0.1)		// remember, "up" on the joystick is a - value, (we use .1 as joystick deadband)
    			{
    				outputDataValues.InfeedTiltTargetPositionInRotationsCmd = outputDataValues.InfeedTiltTargetPositionInRotationsCmd + 0.005;
    				
    				// If the position is greater than 90 degrees = 0.25 rotations, prevent infeed from continuing to drive up
    				if (outputDataValues.InfeedTiltTargetPositionInRotationsCmd > RobotMap.INFEED_TILT_STORED_POSITION_CMD)
    				{
    					outputDataValues.InfeedTiltTargetPositionInRotationsCmd = RobotMap.INFEED_TILT_STORED_POSITION_CMD;
    				}
    			}
    			else if (inputDataValues.InfeedRawTiltCmd > 0.1)	// remember, "down" on the joystick is a + value, (we use .1 as joystick deadband)
    			{
    				outputDataValues.InfeedTiltTargetPositionInRotationsCmd = outputDataValues.InfeedTiltTargetPositionInRotationsCmd - 0.005;
    				
    				// If the position is less than 0 degrees = -0.13 rotations, prevent infeed from continuing to drive down
    				if (outputDataValues.InfeedTiltTargetPositionInRotationsCmd < RobotMap.INFEED_TILT_LOWER_LIMIT)
    				{
    					outputDataValues.InfeedTiltTargetPositionInRotationsCmd = RobotMap.INFEED_TILT_LOWER_LIMIT;
    				}
    			}
    			else
    			{
    				// else we are within the joystick deadband, so do nothing
    			}
    		}
    	}
    	else if (_infeedTiltMtr.getControlMode() == CANTalon.TalonControlMode.PercentVbus)
    	{
    		// we determined that 9% will roughly hold the axis at its current position when the axis is near flat
    		outputDataValues.InfeedTiltMtrVelocityCmd = 0.09;
    	}
    	
    	// =====================================
    	// Step 2.3 Infeed Acquisition 
    	// =====================================
    	// Run infeed motors based on command from acquire and release buttons
    	if(inputDataValues.IsInfeedAcquireBtnPressed && inputDataValues.IsInfeedReleaseBtnPressed)
    	{
    	}
    	else if (inputDataValues.IsInfeedAcquireBtnPressed && !inputDataValues.IsInfeedReleaseBtnPressed)
    	{
    		outputDataValues.InfeedAcqMtrVelocityCmd = 1.0;
    	}
    	else if (!inputDataValues.IsInfeedAcquireBtnPressed && inputDataValues.IsInfeedReleaseBtnPressed)
    	{
    		outputDataValues.InfeedAcqMtrVelocityCmd = -1.0;
    	}
    	else
    	{
    		outputDataValues.InfeedAcqMtrVelocityCmd = 0.0;
    	}
    	
    	// =====================================
    	// Step 2.4: Turret 
    	// =====================================
    	
    	// Change turret position command based on operator input
    	if (_turretMtr.getControlMode() == CANTalon.TalonControlMode.PercentVbus)
    	{
    		if (inputDataValues.IsTurretCWButtonPressed && !inputDataValues.IsTurretCCWButtonPressed)
    		{
    			outputDataValues.TurretVelocityCmd = 0.1;
    		}
    		else if (!inputDataValues.IsTurretCWButtonPressed && inputDataValues.IsTurretCCWButtonPressed)
    		{
    			outputDataValues.TurretVelocityCmd = -0.1;
    		}
    		else
    		{
    			outputDataValues.TurretVelocityCmd = 0.0;
    		}
    	}
    	else if (_turretMtr.getControlMode() == CANTalon.TalonControlMode.Position)
    	{
			if (inputDataValues.IsTurretCWButtonPressed && !inputDataValues.IsTurretCCWButtonPressed)		
			{
				if(!workingDataValues.IsTurretCWButtonPressedLastScan)
				{
					double NewTurretTargetPositionCmd = outputDataValues.TurretTargetPositionCmd + 0.05;
					outputDataValues.TurretTargetPositionCmd = CalcTurretTargetPosition(NewTurretTargetPositionCmd);
					
					// If the position is greater than 90 degrees = 0.25 rotations, prevent infeed from continuing to drive up
					//if (outputDataValues.InfeedTiltTargetPositionInRotationsCmd > RobotMap.INFEED_TILT_STORED_POSITION_CMD)
					//{
					//	outputDataValues.InfeedTiltTargetPositionInRotationsCmd = RobotMap.INFEED_TILT_STORED_POSITION_CMD;
					//}
				}
			}
			else if (!inputDataValues.IsTurretCWButtonPressed && inputDataValues.IsTurretCCWButtonPressed)	
			{
				if(!workingDataValues.IsTurretCCWButtonPressedLastScan)
				{
					double NewTurretTargetPositionCmd = outputDataValues.TurretTargetPositionCmd - 0.05;
					outputDataValues.TurretTargetPositionCmd = CalcTurretTargetPosition(NewTurretTargetPositionCmd);
					
					// If the position is greater than 90 degrees = 0.25 rotations, prevent infeed from continuing to drive up
					//if (outputDataValues.InfeedTiltTargetPositionInRotationsCmd > RobotMap.INFEED_TILT_STORED_POSITION_CMD)
					//{
					//	outputDataValues.InfeedTiltTargetPositionInRotationsCmd = RobotMap.INFEED_TILT_STORED_POSITION_CMD;
					//}
				}
			}
    	}
    	
    	//outputDataValues.TurretTargetPositionCmd = CalcTurretTargetPosition(workingDataValues.TurretTurnDegreesCmd);
    	    	
    	// ============================
    	// Step 2.5: Slider
    	// ============================
    	if (_sliderMtr.getControlMode() == CANTalon.TalonControlMode.PercentVbus)
    	{
    		if ((inputDataValues.IsSliderFwdBtnPressed == true) && (!inputDataValues.IsSliderRevBtnPressed == false))
    		{
    			outputDataValues.SliderVelocityCmd = 0.1;
    		}
    		else if ((!inputDataValues.IsSliderFwdBtnPressed == false) && (inputDataValues.IsSliderRevBtnPressed == true))
    		{
    			outputDataValues.SliderVelocityCmd = -0.1;
    		}
    		else
    		{
    			outputDataValues.SliderVelocityCmd = 0.0;
    		}
    	}
    	else if (_sliderMtr.getControlMode() == CANTalon.TalonControlMode.Position)
    	{
    		if ((inputDataValues.IsSliderFwdBtnPressed == true) && (inputDataValues.IsSliderRevBtnPressed == false))
    		{
    			if (!workingDataValues.IsSliderFwdBtnPressedLastScan)	// debounce keypress
    			{
    				double newSliderTargetPosition = inputDataValues.SliderCurrentPosition + 8.0;
    				outputDataValues.SliderTargetPositionCmd = CalcSliderTargetPositionCmd(newSliderTargetPosition);
    				DriverStation.reportError(String.format("..Info: New Forward Target Slider Position: {0}", newSliderTargetPosition), false);
    				
    			}
    		}
    		else if ((inputDataValues.IsSliderFwdBtnPressed == false) && (inputDataValues.IsSliderRevBtnPressed == true))
    		{
    			if (!workingDataValues.IsSliderRevBtnPressedLastScan)	// debounce keypress
    			{
    				double newSliderTargetPosition = inputDataValues.SliderCurrentPosition - 8.0;
    				outputDataValues.SliderTargetPositionCmd = CalcSliderTargetPositionCmd(newSliderTargetPosition);
    				DriverStation.reportError(String.format("..Info: New Reverse Target Slider Position: {0}", newSliderTargetPosition), false);
    			}
    		}
    		else
    		{
    			// else no change
    		}
    	}
    	    	
    	// ============================
    	//  Step 2.6: Shooter 
    	// ============================
    	if (inputDataValues.ShooterRawVelocityCmd > 0.1)
    	{    	
    		if (_shooterMasterMtr.getControlMode() == CANTalon.TalonControlMode.Speed)
    		{
    			outputDataValues.ShooterMtrVelocityCmd = RobotMap.SHOOTER_TARGET_MOTOR_RPM;
    		}
    		else if (_shooterMasterMtr.getControlMode() == CANTalon.TalonControlMode.PercentVbus)
    		{
    			outputDataValues.ShooterMtrVelocityCmd = workingDataValues.DriveSpeedScalingFactor;
    		}
    	}
    	else
    	{
    		outputDataValues.ShooterMtrVelocityCmd = 0.0;
    	}
    	
    	// ============================
    	// Step 2.7: Kicker
    	// ============================
    	if (outputDataValues.ShooterMtrVelocityCmd > 0.1)
    	{
    		outputDataValues.KickerMtrVelocityCmd = RobotMap.KICKER_TARGET_PERCENT_VBUS_CMD;
    	}
    	else
    	{
    		outputDataValues.KickerMtrVelocityCmd = 0.0;
    	}
    	    	
    	// =====================================
    	// Step 3: Push the target Outputs out to the physical devices
    	// =====================================
    	
    	// ==========================
    	// 3.1 Handle Puma Front, Back and Shifter Solenoids
    	//		Solenoids work like a toggle, the current value is retained until it is changed
    	// ==========================
    	if (!workingDataValues.IsPumaFrontToggleBtnPressedLastScan && inputDataValues.IsPumaFrontToggleBtnPressed)
    	{
    		if (outputDataValues.PumaFrontSolenoidPosition == RobotMap.PUMA_FRONT_SOLENOID_OPEN_POSITION)
    		{
    			outputDataValues.PumaFrontSolenoidPosition = RobotMap.PUMA_FRONT_SOLENOID_CLOSED_POSITION;
    		}
    		else
    		{
    			outputDataValues.PumaFrontSolenoidPosition = RobotMap.PUMA_FRONT_SOLENOID_OPEN_POSITION;
    		}
    	}
    	
    	if (!workingDataValues.IsPumaBackToggleBtnPressedLastScan && inputDataValues.IsPumaBackToggleBtnPressed)
    	{
    		if (outputDataValues.PumaBackSolenoidPosition == RobotMap.PUMA_BACK_SOLENOID_OPEN_POSITION)
    		{
    			outputDataValues.PumaBackSolenoidPosition = RobotMap.PUMA_BACK_SOLENOID_CLOSED_POSITION;
    		}
    		else
    		{
    			outputDataValues.PumaBackSolenoidPosition = RobotMap.PUMA_BACK_SOLENOID_OPEN_POSITION;
    		}
    	}
    	
    	if (inputDataValues.IsShifterToggleHighBtnPressed && !inputDataValues.IsShifterToggleLowBtnPressed)
    	{
    		outputDataValues.ShifterSolenoidPosition = RobotMap.SHIFTER_SOLENOID_OPEN_POSITION;
    	}
    	else if (!inputDataValues.IsShifterToggleHighBtnPressed && inputDataValues.IsShifterToggleLowBtnPressed)
    	{
    		outputDataValues.ShifterSolenoidPosition = RobotMap.SHIFTER_SOLENOID_CLOSED_POSITION;
    	}
    	else
    	{
    	}
    	
    	// set solenoids state
    	_pumaFrontSolenoid.set(outputDataValues.PumaFrontSolenoidPosition);
    	_pumaBackSolenoid.set(outputDataValues.PumaBackSolenoidPosition);
    	_shifterSolenoid.set(outputDataValues.ShifterSolenoidPosition);
    	
    	// set motor commmands
    	_robotDrive.arcadeDrive(outputDataValues.ArcadeDriveThrottleAdjCmd, outputDataValues.ArcadeDriveTurnAdjCmd, false);
    	
    	_infeedAcqMtr.set(outputDataValues.InfeedAcqMtrVelocityCmd);
    	
    	if (_infeedTiltMtr.getControlMode() == CANTalon.TalonControlMode.Position)
    	{
    		_infeedTiltMtr.set(outputDataValues.InfeedTiltTargetPositionInRotationsCmd);
    	}
    	else if (_infeedTiltMtr.getControlMode() == CANTalon.TalonControlMode.PercentVbus)
    	{
    		_infeedTiltMtr.set(outputDataValues.InfeedTiltMtrVelocityCmd);
    	}
    	 
    	if (_turretMtr.getControlMode() == CANTalon.TalonControlMode.Position)
    	{
    		_turretMtr.set(outputDataValues.TurretTargetPositionCmd);
    	}
    	else if (_turretMtr.getControlMode() == CANTalon.TalonControlMode.PercentVbus)
    	{
    		_turretMtr.set(outputDataValues.TurretVelocityCmd);
    	}
    	
    	_kickerMtr.set(outputDataValues.KickerMtrVelocityCmd);
    	
    	_shooterMasterMtr.set(outputDataValues.ShooterMtrVelocityCmd);
    	
    	if (_sliderMtr.getControlMode() == CANTalon.TalonControlMode.Position)
    	{
    		_sliderMtr.set(outputDataValues.SliderTargetPositionCmd);
    	}
    	else if (_sliderMtr.getControlMode() == CANTalon.TalonControlMode.PercentVbus)
    	{
    		_sliderMtr.set(outputDataValues.SliderVelocityCmd);
    	}
    	
    	// ==========================
    	// 4.0 Update Dashboard
    	// ==========================
    	UpdateDashboard(_robotLiveData);
    	
    	workingDataValues.LastScanDT = new Date();  	
    	
    	// optionally send message to drivers station
    	if(outputDataValues.DriversStationMsg != null 
    			&& outputDataValues.DriversStationMsg.length() > 0)
    	{
    		DriverStation.reportError(outputDataValues.DriversStationMsg, false);
    	}
    	
    	// ==========================
    	// 5.0 Optional Data logging
    	// ==========================
    	if(workingDataValues.IsLoggingEnabled == true)
    	{
    		_dataLogger.WriteData(_robotLiveData);
    	}
    	// set last scan DT

    	// ==========================
    	// 6.0 Stuff we want to do at the very end (because they operate as toggles)
    	// ==========================
    	workingDataValues.IsPumaFrontToggleBtnPressedLastScan = inputDataValues.IsPumaFrontToggleBtnPressed;
    	workingDataValues.IsPumaBackToggleBtnPressedLastScan = inputDataValues.IsPumaBackToggleBtnPressed;
    	workingDataValues.IsShifterToggleBtnPressedLastScan = inputDataValues.IsShifterToggleHighBtnPressed;
    	workingDataValues.IsSliderFwdBtnPressedLastScan = inputDataValues.IsSliderFwdBtnPressed;
    	workingDataValues.IsSliderRevBtnPressedLastScan = inputDataValues.IsSliderRevBtnPressed;
    	workingDataValues.IsTurretCWButtonPressedLastScan = inputDataValues.IsTurretCWButtonPressed;
    	workingDataValues.IsTurretCCWButtonPressedLastScan = inputDataValues.IsTurretCCWButtonPressed;
    }
    
    public void disabledPeriodic()
    {
    	if(_dataLogger != null)
    	{
    		_dataLogger.close();
    		_dataLogger = null;
    	}
    }
    
    // ==========  Absolute Axis Homing Logic ==============================================
    
    private double CalcInfeedTiltAngleInRotations(double InfeedAssemblyAngleInDegrees, double HomePositionAngleInDegrees)
    {   
    	double InfeedAssemblyTiltTargetPositionInRotationsCmd = InfeedAssemblyAngleInDegrees/360.0;
    	double HomePositionAngleInRotations = HomePositionAngleInDegrees/360.0;
    	
    	double InfeedMtrTiltTargetPositionInRotationsCmd = (RobotMap.INFEED_TILT_GEAR_RATIO * (InfeedAssemblyTiltTargetPositionInRotationsCmd
    															- HomePositionAngleInRotations)) + HomePositionAngleInRotations;
    	
    	return -0.11;
    }
    
    // caluclate the appropriate # of leadscrew rotations
    private double CalcSliderTargetPositionCmd(double targetPositionFromHomeInRotations)
    {
    	// Notes:
    	//	The encoder is directly coupled to the leadscrew
    	//		leadscrew pitch : 16 rev / inch
    	//		quad encoder	: 1024 pulses / rev x 4 = 4096 counts / rev
    	//	We setup the TALON is use API Unit Scaling by using the ConfigEncoderCodesPerRev in the axis home method
    	
    	// protect the axis by enforcing guard rails on what can be requested
    	if(targetPositionFromHomeInRotations > RobotMap.SLIDER_FWD_MAX_TRAVEL_IN_ROTATIONS)
    	{
    		targetPositionFromHomeInRotations = RobotMap.SLIDER_FWD_MAX_TRAVEL_IN_ROTATIONS;
    		DriverStation.reportError("Slider already at forward limit", false);
    	}
    	else if (targetPositionFromHomeInRotations < RobotMap.SLIDER_REV_MAX_TRAVEL_IN_ROTATIONS)
    	{
    		targetPositionFromHomeInRotations = RobotMap.SLIDER_REV_MAX_TRAVEL_IN_ROTATIONS;
    		DriverStation.reportError("Slider already at reverse limit", false);
    	}
    	
    	double sliderTargetPositionCmd = targetPositionFromHomeInRotations;
    	    	
    	return sliderTargetPositionCmd;
    }
    
    private double CalcTurretTargetPosition(double TurretAngleInRotations)
    {
    	//double TurretPositionInEncoderCounts = (TurretAngleInRotations * 360)/ RobotMap.TURRET_TRAVEL_DEGREES_PER_COUNT;
    	//return TurretPositionInEncoderCounts;
    	if (TurretAngleInRotations > RobotMap.TURRET_MAX_TRAVEL_IN_ROTATIONS)
    	{
    		TurretAngleInRotations = RobotMap.TURRET_MAX_TRAVEL_IN_ROTATIONS;
    		DriverStation.reportError("Turret already at forward limit", false);
    	}
    	else if (TurretAngleInRotations < RobotMap.TURRET_MIN_TRAVEL_IN_ROTATIONS)
    	{
    		TurretAngleInRotations = RobotMap.TURRET_MIN_TRAVEL_IN_ROTATIONS;
    		DriverStation.reportError("Turret already at reverse limit", false);
    	}
    	
    	double TurretSetPositionInRotations = TurretAngleInRotations;
    	return TurretSetPositionInRotations;
    }
    
    
    // This method Zeros (ie Homes) the Infeed Tilt Axis
	private void ZeroInfeedTiltAxis(RobotData p_robotLiveData) 
	{
		//get local references to make variable references shorter
    	InputData inputDataValues = _robotLiveData.InputDataValues;
    	WorkingData workingDataValues = _robotLiveData.WorkingDataValues;
    	OutputData outputDataValues = _robotLiveData.OutputDataValues;
    	
    	boolean isTimeout = false; //control whether we want this method to run at startup
    	boolean isOnHomeSwitch = !_infeedTiltMtr.isFwdLimitSwitchClosed();	// switch is normally closed
    	
    	// zero the current encoder reading
    	_infeedTiltMtr.setPosition(0);	
    	
    	// if we are not already on the up limit switch
    	if(!isOnHomeSwitch)
    	{
	    	// start out in %VBUS mode
	    	_infeedTiltMtr.changeControlMode(CANTalon.TalonControlMode.PercentVbus);
	    	
	    	// drive the axis up at 15%
	    	outputDataValues.InfeedTiltMtrVelocityCmd = 0.19;
	    	_infeedTiltMtr.set(outputDataValues.InfeedTiltMtrVelocityCmd);
	    	
	    	long startTime = System.currentTimeMillis();
	    	long elapsedTime = 0L;
	    	long maxTimeInMSec = 10000; // 10 secs
	    	
	    	// if we are not on the limit switch, drive up until we hit it but only wait for 10 secs max
	    	while(!isOnHomeSwitch && !isTimeout)
	    	{	
	        	elapsedTime = (new Date().getTime() - startTime);
	    		
	    		if (elapsedTime  >= maxTimeInMSec)
	    		{
	    			isTimeout = true;
	    		}
	    		isOnHomeSwitch = !_infeedTiltMtr.isFwdLimitSwitchClosed();	// switch is normally closed
	    	}
    	}
    	
    	// we are on the UP Limit Switch (and we did not timeout)
    	if(!isTimeout)
    	{
    		
	    	_infeedTiltMtr.changeControlMode(CANTalon.TalonControlMode.Position);
	    	
	    	// once we hit the home switch, reset the encoder	- this is at approx 106deg
	    	
	    	_infeedTiltMtr.setPosition(RobotMap.INFEED_TILT_HOME_POSITION_IN_ROTATIONS);	
	    	//_infeedTiltMtr.setEncPosition(newPosition);
	    	try {
	    		// sleep a little to let the zero occur
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	    	
	    	// setup the PID Loop
	    	_infeedTiltMtr.setPID(RobotMap.INFEED_TILT_KP, RobotMap.INFEED_TILT_KI, RobotMap.INFEED_TILT_KD, RobotMap.INFEED_TILT_KF, RobotMap.INFEED_TILT_IZONE, RobotMap.INFEED_TILT_RAMPRATE, RobotMap.INFEED_TILT_PROFILE);
	    	_infeedTiltMtr.setProfile(RobotMap.INFEED_TILT_PROFILE);
	    	_infeedTiltMtr.setCloseLoopRampRate(RobotMap.INFEED_TILT_RAMPRATE);
	    	_infeedTiltMtr.setVoltageRampRate(5);
	    	
	    	// write to the operator's console log window
	    	DriverStation.reportError("..Infeed Tilt Axis Zeroed, Chging to Position Ctrl Mode.", false);
	    	
	    	outputDataValues.InfeedTiltTargetPositionInRotationsCmd = (RobotMap.INFEED_TILT_HOME_POSITION_IN_ROTATIONS);
	    	
	    	// finally mark the axis as zeroed
	    	_isInfeedTiltAxisZeroedYet = true;
    	}
    	else
    	{
    		// write to the operator's console log window
	    	DriverStation.reportError("..ERROR: Timeout in Infeed Tilt Axis Zero procedure.", false);
    	}
	}

    // This method Zeros (ie Homes) the Slider Axis
    private void ZeroSliderAxis(RobotData p_robotLiveData) 
    {
    	//get local references to make variable references shorter
    	InputData inputDataValues = _robotLiveData.InputDataValues;
    	WorkingData workingDataValues = _robotLiveData.WorkingDataValues;
    	OutputData outputDataValues = _robotLiveData.OutputDataValues;
    	
    	boolean isTimeout = false; //control whether we want this method to run at startup
    	boolean isOnHomeSwitch = !_sliderMtr.isRevLimitSwitchClosed();	// switch is normally closed
    	
    	// zero the current encoder reading
    	//_sliderMtr.setPosition(0);	
    	
    	// if we are not already on the up limit switch
    	if(!isOnHomeSwitch)
    	{
	    	// start out in %VBUS mode
	    	_sliderMtr.changeControlMode(CANTalon.TalonControlMode.PercentVbus);
	    	
	    	// drive the axis down at 5%
	    	outputDataValues.SliderVelocityCmd = -0.60;
	    	_sliderMtr.set(outputDataValues.SliderVelocityCmd);
	    	
	    	long startTime = System.currentTimeMillis();
	    	long elapsedTime = 0L;
	    	long maxTimeInMSec = 10000; // 10 secs
	    	
	    	// if we are not on the limit switch, drive up until we hit it but only wait for 10 secs max
	    	while(!isOnHomeSwitch && !isTimeout)
	    	{	
	        	elapsedTime = (new Date().getTime() - startTime);
	    		
	    		if (elapsedTime  >= maxTimeInMSec)
	    		{
	    			isTimeout = true;
	    		}
	    		
	    		isOnHomeSwitch = !_sliderMtr.isRevLimitSwitchClosed();	// switch is normally closed
	    	}
    	}
    	
    	if(!isTimeout)
    	{
    		
	    	_sliderMtr.changeControlMode(CANTalon.TalonControlMode.Position);
	    	
	    	// once we hit it reset the encoder	- this is at approx 90deg
	    	double SliderHomePosition = 0;
	    	
	    	_sliderMtr.setPosition(SliderHomePosition);	
	    	//_infeedTiltMtr.setEncPosition(newPosition);
	    	try {
	    		// sleep a little to let the zero occur
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	    	
	    	// setup the PID Loop
	    	_sliderMtr.setPID(RobotMap.SLIDER_KP, RobotMap.SLIDER_KI, RobotMap.SLIDER_KD, RobotMap.SLIDER_KF, RobotMap.SLIDER_IZONE, RobotMap.SLIDER_RAMPRATE, RobotMap.SLIDER_PROFILE);
	    	_sliderMtr.setProfile(RobotMap.SLIDER_PROFILE);
	    	// write to the operator's console log window
	    	DriverStation.reportError("..Slider Axis Zeroed, Chging to Position Ctrl Mode.", false);
	    		    	
	    	outputDataValues.SliderTargetPositionCmd = RobotMap.SLIDER_DEFAULT_TARGET_POSITION;
	    	_sliderMtr.set(outputDataValues.SliderTargetPositionCmd);
	    	// finally mark the axis as zeroed
	    	_isSliderAxisZeroedYet = true;
    	}
    	else
    	{
    		// write to the operator's console log window
	    	DriverStation.reportError("..ERROR: Timeout in Slider Axis Zero procedure.", false);
    	}
    	
	}
	
	// This method Zeros (ie Homes) the Turret Axis
	private void ZeroTurretAxis(RobotData p_robotLiveData) 
	{
		//get local references to make variable references shorter
    	InputData inputDataValues = _robotLiveData.InputDataValues;
    	WorkingData workingDataValues = _robotLiveData.WorkingDataValues;
    	OutputData outputDataValues = _robotLiveData.OutputDataValues;
    	
    	boolean isTimeout = false; //control whether we want this method to run at startup
    	boolean isOnApproachingHomeSwitch = _turretApproachingHomeLimitSwitch.get();
    	
    	// zero the current encoder reading
    	//_turretMtr.setPosition(0);	
    	
    	// if we are not already on the up limit switch
    	if(!isOnApproachingHomeSwitch)
    	{
	    	// start out in %VBUS mode
	    	_turretMtr.changeControlMode(CANTalon.TalonControlMode.PercentVbus);
	    	
	    	// drive the axis up at 15%
	    	outputDataValues.TurretVelocityCmd = 0.06;
	    	_turretMtr.set(outputDataValues.TurretVelocityCmd);
	    	
	    	long startTime = System.currentTimeMillis();
	    	long elapsedTime = 0L;
	    	long maxTimeInMSec = 5000; // 2 secs
	    	
	    	// if we are not on the limit switch, drive up until we hit it but only wait for 10 secs max
	    	while(!isOnApproachingHomeSwitch && !isTimeout)
	    	{	
	        	elapsedTime = (new Date().getTime() - startTime);
	    		
	    		if (elapsedTime  >= maxTimeInMSec)
	    		{
	    			isTimeout = true;
	    		}
	    		isOnApproachingHomeSwitch = _turretApproachingHomeLimitSwitch.get();	// switch is normally open
	    	}
    	}
    	
    	boolean isOnHomeSwitch = _turretHomeLimitSwitch.get();	// switches are normally open
    	
    	if(!isTimeout)
    	{
    		long startTime = System.currentTimeMillis();
	    	long elapsedTime = 0L;
	    	long maxTimeInMSec = 5000; // 5 secs
	    	
	    	// if we are not on the limit switch, drive up until we hit it but only wait for 10 secs max
	    	while(!isOnHomeSwitch && !isTimeout)
	    	{	
	        	elapsedTime = (new Date().getTime() - startTime);
	    		
	    		if (elapsedTime  >= maxTimeInMSec)
	    		{
	    			isTimeout = true;
	    		}
	    		isOnHomeSwitch = _turretHomeLimitSwitch.get();	// switch is normally open
	    	}
    	}
    	
    	// we are on the ZERO Limit Switch (and we did not timeout)
    	if(!isTimeout)
    	{
    		// stop driving the axis
    		outputDataValues.TurretVelocityCmd = 0;
	    	_turretMtr.set(outputDataValues.TurretVelocityCmd);
    		
	    	// now switch to position loop mode
	    	_turretMtr.changeControlMode(CANTalon.TalonControlMode.Position);
	    	
	    	// once we hit it reset the encoder
	    	double homePosition = 0;
	    	_turretMtr.setPosition(homePosition);	

	    	try {
	    		// sleep a little to let the zero occur
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	    	
	    	// setup the PID Loop
	    	_turretMtr.setPID(RobotMap.TURRET_KP, RobotMap.TURRET_KI, RobotMap.TURRET_KD, RobotMap.TURRET_KF, RobotMap.TURRET_IZONE, RobotMap.TURRET_RAMPRATE, RobotMap.TURRET_PROFILE);
	    	_turretMtr.setProfile(RobotMap.TURRET_PROFILE);
	    	
	    	// write to the operator's console log window
	    	DriverStation.reportError("..Turret Axis Zeroed, Chg to Positon Ctrl Mode.", false);
	    	
	    	// drive to default position
	    	workingDataValues.TurretTurnRotationsCmd = 0;
	    	outputDataValues.TurretTargetPositionCmd 
	    		= CalcTurretTargetPosition(workingDataValues.TurretTurnRotationsCmd);
	    	_turretMtr.set(outputDataValues.TurretTargetPositionCmd);
	    	
	    	// finally mark the axis as zeroed
	    	_isTurretAxisZeroedYet = true;
    	}
    	else
    	{
    		// write to the operator's console log window
	    	DriverStation.reportError("..ERROR: Timeout in Turret Axis Zero procedure.", false);
    	}
	}
	
    
	// For PID velocity control we need to convert Target RPM into encoder counts per 100mSec
    private double CalcShooterVelociytInNativeUnitsPer100mSec(int targetWheelRPM)
    {
    	//  (1 / 60) => 1 min / 60 Sec
    	//  (1 / 10) => 1 sec / 10 (100mSec chunks)
    	double encoderCountsPer100mSec = targetWheelRPM * (1 / 60) * (1 / 10) * (RobotMap.SHOOTER_ENCODER_COUNTS_PER_REV * RobotMap.SHOOTER_ENCODER_QUAD_MULTIPLIER);
    	
    	return encoderCountsPer100mSec;
    }
    
	
    private double CalcShooterFeedFwdGain(double shooterVelocityInNativeUnitsPer100mSec)
    {
    	//  (1 / 60) => 1 min / 60 Sec
    	//  (1 / 10) => 1 sec / 10 (100mSec chunks)
    	double targetShooterFeedFwdGain = 1023 / shooterVelocityInNativeUnitsPer100mSec;
    	
    	return targetShooterFeedFwdGain;
    }
    
    
    /**
    / This method updates all of the input values
	**/
    private void UpdateInputAndCalcWorkingDataValues(InputData inputDataValues, WorkingData workingDataValues)
    {	
    	// ==========================
    	// 1.1 get hign resolution timer
    	// ==========================
    	inputDataValues.FPGATimeMicroSecs = Utility.getFPGATime();
    	
    	// ==========================
    	// 1.2 get values from the gamepads
    	// ==========================
    	inputDataValues.IsScaleDriveSpeedUpBtnPressed = _driverGamepad.getRawButton(RobotMap.DRIVER_GAMEPAD_SCALE_SPEED_UP_BTN);
    	inputDataValues.IsScaleDriveSpeedDownBtnPressed = _driverGamepad.getRawButton(RobotMap.DRIVER_GAMEPAD_SCALE_SPEED_DOWN_BTN);
    	inputDataValues.IsPumaFrontToggleBtnPressed = _driverGamepad.getRawButton(RobotMap.DRIVER_GAMEPAD_PUMA_FRONT_TOGGLE_BTN);
    	inputDataValues.IsPumaBackToggleBtnPressed = _driverGamepad.getRawButton(RobotMap.DRIVER_GAMEPAD_PUMA_BACK_TOGGLE_BTN);
    	inputDataValues.IsShifterToggleHighBtnPressed = _driverGamepad.getRawButton(RobotMap.DRIVER_GAMEPAD_SHIFTER_TOGGLE_HIGH_BTN);
    	inputDataValues.IsShifterToggleLowBtnPressed = _driverGamepad.getRawButton(RobotMap.DRIVER_GAMEPAD_SHIFTER_TOGGLE_LOW_BTN);
    	inputDataValues.IsPrintDataBtnPressed = _driverGamepad.getRawButton(RobotMap.DRIVER_GAMEPAD_PRINT_DATA_BTN);
    	
    	inputDataValues.IsTurretCWButtonPressed = _operatorGamepad.getRawButton(RobotMap.OPERATOR_GAMEPAD_TURRET_CW_BTN);
    	inputDataValues.IsTurretCCWButtonPressed = _operatorGamepad.getRawButton(RobotMap.OPERATOR_GAMEPAD_TURRET_CCW_BTN);
    	inputDataValues.IsSliderFwdBtnPressed = _operatorGamepad.getRawButton(RobotMap.OPERATOR_GAMEPAD_SLIDER_FWD_BTN);
    	inputDataValues.IsSliderRevBtnPressed = _operatorGamepad.getRawButton(RobotMap.OPERATOR_GAMEPAD_SLIDER_REV_BTN);
    	inputDataValues.IsInfeedAcquireBtnPressed = _operatorGamepad.getRawButton(RobotMap.OPERATOR_GAMEPAD_INFEED_ACQUIRE_BTN);
    	inputDataValues.IsInfeedReleaseBtnPressed = _operatorGamepad.getRawButton(RobotMap.OPERATOR_GAMEPAD_INFEED_RELEASE_BTN);
    	inputDataValues.IsInfeedTiltStoreBtnPressed = _operatorGamepad.getRawButton(RobotMap.OPERATOR_GAMEPAD_INFEED_TILT_STORE_BTN);
    	inputDataValues.IsInfeedTiltDeployBtnPressed = _operatorGamepad.getRawButton(RobotMap.OPERATOR_GAMEPAD_INFEED_TILT_DEPLOY_BTN);
    	
    	// remember:	on gamepads fwd/up = -1 and rev/down = +1 so invert the values
    	inputDataValues.ArcadeDriveThrottleRawCmd = _driverGamepad.getRawAxis(RobotMap.DRIVER_GAMEPAD_THROTTLE_AXIS_JOYSTICK);
    	inputDataValues.ArcadeDriveTurnRawCmd = _driverGamepad.getRawAxis(RobotMap.DRIVER_GAMEPAD_TURN_AXIS_JOYSTICK);
    	
    	inputDataValues.ShooterRawVelocityCmd = _operatorGamepad.getRawAxis(RobotMap.OPERATOR_GAMEPAD_SHOOTER_AXIS);
    	
    	inputDataValues.InfeedRawTiltCmd = _operatorGamepad.getRawAxis(RobotMap.OPERATOR_GAMEPAD_INFEED_TILT_AXIS);
    	//inputDataValues.InfeedTiltUpCmd = _driverGamepad.getRawAxis(RobotMap.DRIVER_GAMEPAD_INFEED_TILT_UP_TRIGGER);
    	//inputDataValues.InfeedTiltDownCmd = _driverGamepad.getRawAxis(RobotMap.DRIVER_GAMEPAD_INFEED_TILT_DOWN_TRIGGER);
 	
    	// ==========================
    	// 1.3 get values from motor controlllers
    	// ==========================
    	//inputDataValues.LeftDriveEncoderCurrentCount = _leftDriveMasterMtr.getPosition();
    	//inputDataValues.RightDriveEncoderCurrentCount = _rightDriveMasterMtr.getPosition();	
    	inputDataValues.InfeedTiltEncoderCurrentCount = _infeedTiltMtr.getPosition();
    	inputDataValues.TurretEncoderCurrentCount = _turretMtr.getPosition();
    	inputDataValues.SliderEncoderCurrentCount = _sliderMtr.getPosition();
    	
    	
    	// velocity control axis
    	/*
    	inputDataValues.ShooterClosedLoopError = _shooterMasterMtr.getClosedLoopError();
    	inputDataValues.ShooterActualSpeed = _shooterMasterMtr.getSpeed();
    	inputDataValues.ShooterActualVToBusVRatio = _shooterMasterMtr.getOutputVoltage() / _shooterMasterMtr.getBusVoltage();
    	inputDataValues.ShooterCurrentBusVoltage = _shooterMasterMtr.getBusVoltage();
    	*/
    	
    	inputDataValues.IsInfeedTiltAxisOnUpLimitSwitch = _infeedTiltMtr.isFwdLimitSwitchClosed();
    	
    	inputDataValues.SliderCurrentPosition = _sliderMtr.getPosition();
    	
    	// ==========================
    	// 1.4 get values from Limit Switches
    	// ==========================
    	inputDataValues.IsTurretHomeLimitSwitchClosed = _turretHomeLimitSwitch.get();
    	inputDataValues.IsTurretApproachingHomeLimitSwitchClosed = _turretApproachingHomeLimitSwitch.get();
    	
    	// ==========================
    	// 1.5 get values from navX
    	// ==========================
    	inputDataValues.NavxIsConnected = _navXSensor.isConnected();
    	inputDataValues.NavxIsCalibrating = _navXSensor.isCalibrating();
    	inputDataValues.NavxYaw = _navXSensor.getYaw();
    	inputDataValues.NavxPitch = _navXSensor.getPitch();
    	inputDataValues.NavxRoll = _navXSensor.getRoll();
    	inputDataValues.NavxCompassHeading = _navXSensor.getCompassHeading();
    	inputDataValues.NavxFusedHeading = _navXSensor.getFusedHeading();
    	inputDataValues.NavxTotalYaw = _navXSensor.getAngle();
    	inputDataValues.NavxYawRateDPS = _navXSensor.getRate();
    	inputDataValues.NavxAccelX = _navXSensor.getWorldLinearAccelX();
    	inputDataValues.NavxAccelY = _navXSensor.getWorldLinearAccelY();
    	inputDataValues.NavxIsMoving = _navXSensor.isMoving();
    	inputDataValues.NavxIsRotating = _navXSensor.isRotating();

    	// =========================
    	// 2.0 Calc Working Values
    	// ==========================
    	
    	// speed units are are sensor's native ticks per 100mSec
    	//  1000 counts => 10 RPS (Rotation per second)
    	
    	// 2.1 Left Axis
    	
		workingDataValues.LeftDriveEncoderLastDeltaCount = (inputDataValues.LeftDriveEncoderCurrentCount 
																- workingDataValues.LeftDriveEncoderLastCount);
		workingDataValues.LeftDriveEncoderTotalDeltaCount = (inputDataValues.LeftDriveEncoderCurrentCount 
																- workingDataValues.LeftDriveEncoderInitialCount);
		workingDataValues.LeftDriveEncoderLastCount = inputDataValues.LeftDriveEncoderCurrentCount;
    	
    	//workingDataValues.LeftDriveEncoderCurrentCPS = _leftDriveMasterMtr.getSpeed() * 10.0;

    	workingDataValues.LeftDriveWheelsCurrentSpeedIPS = workingDataValues.LeftDriveEncoderCurrentCPS
    														* RobotMap.LEFT_DRIVE_TRAVEL_DISTANCE_INCHES_PER_COUNT;

    	workingDataValues.LeftDriveGearBoxCurrentRPM = (workingDataValues.LeftDriveEncoderCurrentCPS 
															* 60										// CPS -> CPM
															/ RobotMap.LEFT_DRIVE_ENCODER_COUNTS_PER_REV);		// CPM -> RPM

		workingDataValues.LeftDriveMotorCurrentRPM = workingDataValues.LeftDriveGearBoxCurrentRPM
														* RobotMap.LEFT_DRIVE_GEAR_BOX_RATIO;
    	
		// 2.2 Right Axis
		workingDataValues.RightDriveEncoderLastDeltaCount = (inputDataValues.RightDriveEncoderCurrentCount 
																- workingDataValues.RightDriveEncoderLastCount);
		workingDataValues.RightDriveEncoderTotalDeltaCount = (inputDataValues.RightDriveEncoderCurrentCount 
																- workingDataValues.RightDriveEncoderInitialCount);
		workingDataValues.RightDriveEncoderLastCount = inputDataValues.RightDriveEncoderCurrentCount;
		
    	//workingDataValues.RightDriveEncoderCurrentCPS = _rightDriveMasterMtr.getSpeed() * 10.0;
    	
    	workingDataValues.RightDriveWheelsCurrentSpeedIPS = workingDataValues.RightDriveEncoderCurrentCPS
    															* RobotMap.RIGHT_DRIVE_TRAVEL_DISTANCE_INCHES_PER_COUNT;
    	
    	workingDataValues.RightDriveGearBoxCurrentRPM = (workingDataValues.RightDriveEncoderCurrentCPS 
															* 60										// CPS -> CPM
															/ RobotMap.RIGHT_DRIVE_ENCODER_COUNTS_PER_REV);		// CPM -> RPM
    	
    	workingDataValues.RightDriveMotorCurrentRPM = workingDataValues.RightDriveGearBoxCurrentRPM
														* RobotMap.RIGHT_DRIVE_GEAR_BOX_RATIO;
    	
    	// 2.3 Turret
    	
    	workingDataValues.TurretEncoderTotalDeltaCount = (inputDataValues.TurretEncoderCurrentCount
    														- workingDataValues.TurretEncoderInitialCount);
    	
    	workingDataValues.TurretEncoderDegreesCount = (workingDataValues.TurretEncoderTotalDeltaCount)
    													* RobotMap.TURRET_TRAVEL_DEGREES_PER_COUNT;
    	
    	
    	// 2.4 Shooter 
    	//inputDataValues.ShooterEncoderCurrentCP100MS = _shooterMasterMtr.getSpeed();
    	
    	// Counts per 100 ms * 60 seconds per minute * 10 (100 ms per second)/ 4096 counts per rev
    	workingDataValues.ShooterWheelCurrentRPM = (inputDataValues.ShooterEncoderCurrentCP100MS * 60 * 10) / 4096  ;
    	
    	
    }	
    
    /**
    / This method updates the Smart Dashboard
	**/
    private void UpdateDashboard(RobotData robotDataValues)
    {
    	//get local references
    	InputData inputDataValues = robotDataValues.InputDataValues;
    	WorkingData workingDataValues = robotDataValues.WorkingDataValues;
    	OutputData outputDataValues = robotDataValues.OutputDataValues;
    	
    	// Smart Dashboard Input
    	//SmartDashboard.putString("SD:AutonMode", inputDataValues.AutonModeRequested.toString());
    	
		// Drive Motors
		SmartDashboard.putNumber("Drive.Btn:SpeedScaleFactor", workingDataValues.DriveSpeedScalingFactor);
		
		SmartDashboard.putNumber("Drive.Left:JoyThrottleRawCmd", inputDataValues.ArcadeDriveThrottleRawCmd);
		SmartDashboard.putNumber("Drive.Right:JoyTurnRawCmd", inputDataValues.ArcadeDriveTurnRawCmd);
				
		SmartDashboard.putNumber("Drive.Left:ArcadeDriveThrottleCmd", outputDataValues.ArcadeDriveThrottleAdjCmd);
		SmartDashboard.putNumber("Drive.Right:ArcadeDriveTurnCmd", outputDataValues.ArcadeDriveTurnAdjCmd);
		/*
		SmartDashboard.putNumber("Drive.Left:EncInitCount", workingDataValues.LeftDriveEncoderInitialCount);
		SmartDashboard.putNumber("Drive.Left:EncCurrCount", inputDataValues.LeftDriveEncoderCurrentCount);
		SmartDashboard.putNumber("Drive.Left:EncDeltaCount", workingDataValues.LeftDriveEncoderTotalDeltaCount);
		
		SmartDashboard.putNumber("Drive.Left:MtrCurSpeedRPM", workingDataValues.LeftDriveMotorCurrentRPM);
		SmartDashboard.putNumber("Drive.Left:GBCurSpeedRPM", workingDataValues.LeftDriveGearBoxCurrentRPM);
		SmartDashboard.putNumber("Drive.Left:EncCurSpeedCPS", workingDataValues.LeftDriveEncoderCurrentCPS);
		SmartDashboard.putNumber("Drive.Left:WheelCurSpeedIPS", workingDataValues.LeftDriveWheelsCurrentSpeedIPS);

		SmartDashboard.putNumber("Drive.Right:EncInitCount", workingDataValues.RightDriveEncoderInitialCount);  
		SmartDashboard.putNumber("Drive.Right:EncCurCount", inputDataValues.RightDriveEncoderCurrentCount);
		SmartDashboard.putNumber("Drive.Right:EncDeltaCount", workingDataValues.RightDriveEncoderTotalDeltaCount);
		
		SmartDashboard.putNumber("Drive.Right:MtrCurSpeedRPM", workingDataValues.RightDriveMotorCurrentRPM);
		SmartDashboard.putNumber("Drive.Right:GBCurSpeedRPM", workingDataValues.RightDriveGearBoxCurrentRPM);
		SmartDashboard.putNumber("Drive.Right:EncCurSpeedCPS", workingDataValues.RightDriveEncoderCurrentCPS);
		SmartDashboard.putNumber("Drive.Right:WheelCurSpeedIPS", workingDataValues.RightDriveWheelsCurrentSpeedIPS);
		*/
		// Turret
		SmartDashboard.putNumber("Turret.EncDeltaCount", workingDataValues.TurretEncoderTotalDeltaCount);
		SmartDashboard.putNumber("Turret.EncDegreesCount", workingDataValues.TurretEncoderDegreesCount);
		//SmartDashboard.putBoolean("Turret.IsTurretTargetBtnPressed", inputDataValues.IsTurretTargetBtnPressed);
		SmartDashboard.putBoolean("Turret.IsTurretEncoderDegreesTargetYet", workingDataValues.IsTurretEncoderDegreesTargetYet);
		SmartDashboard.putNumber("Turret.TurnDegreesCmd", workingDataValues.TurretTurnRotationsCmd);
		// Infeed
		SmartDashboard.putNumber("Infeed.RawTiltCmd", inputDataValues.InfeedRawTiltCmd);
		SmartDashboard.putNumber("InfeedAcqMtrVelocityCmd", outputDataValues.InfeedAcqMtrVelocityCmd);
		SmartDashboard.putNumber("InfeedTiltMtrVelocityCmd", outputDataValues.InfeedTiltMtrVelocityCmd);
		
		// Puma Drive
		SmartDashboard.putBoolean("IsInfeedAcquireBtnPressed", inputDataValues.IsInfeedAcquireBtnPressed);
		SmartDashboard.putBoolean("IsInfeedReleaseBtnPressed", inputDataValues.IsInfeedReleaseBtnPressed);
		SmartDashboard.putBoolean("IsPumaFrontToggleBtnPressed", inputDataValues.IsPumaFrontToggleBtnPressed);
		SmartDashboard.putBoolean("IsPumaBackToggleBtnPressed", inputDataValues.IsPumaBackToggleBtnPressed);
		
		// Shooter
		SmartDashboard.putNumber("Shooter.EncoderCurrentCP100MS", inputDataValues.ShooterEncoderCurrentCP100MS);
		SmartDashboard.putNumber("Slider.NumberOfClicks", outputDataValues.SliderTargetPositionCmd);

		// Power Distribution Panel
		
		SmartDashboard.putNumber("PDP.CurrentVoltage", inputDataValues.ShooterCurrentBusVoltage);
		// Logging
		SmartDashboard.putBoolean("Log:IsLoggingEnabled", workingDataValues.IsLoggingEnabled);
		SmartDashboard.putString("Log:LogFilePathName", workingDataValues.LogFilePathName); 
		/*
		SmartDashboard.putBoolean("NavX_IsConnected", inputDataValues.NavxIsConnected);
        SmartDashboard.putBoolean("NavX_IsCalibrating", inputDataValues.NavxIsCalibrating);
        SmartDashboard.putNumber("NavX_Yaw", inputDataValues.NavxYaw);
        SmartDashboard.putNumber("NavX_Pitch", inputDataValues.NavxPitch);
        SmartDashboard.putNumber("NavX_Roll", inputDataValues.NavxRoll);
        SmartDashboard.putNumber("NavX_CompassHeading", inputDataValues.NavxCompassHeading);
        SmartDashboard.putNumber("NavX_FusedHeading", inputDataValues.NavxFusedHeading); 
        SmartDashboard.putNumber("NavX_TotalYaw", inputDataValues.NavxTotalYaw); 
        SmartDashboard.putNumber("NavX_YawRateDPS", inputDataValues.NavxYawRateDPS); 
        SmartDashboard.putNumber("NavX_Accel_X", inputDataValues.NavxAccelX); 
        SmartDashboard.putNumber("NavX_Accel_Y", inputDataValues.NavxAccelY); 
        SmartDashboard.putBoolean("NavX_IsMoving", inputDataValues.NavxIsMoving); 
        SmartDashboard.putBoolean("NavX_IsRotating", inputDataValues.NavxIsRotating); 
		*/
        SmartDashboard.putString("Misc:LastUpdateDT", ZonedDateTime.now().toString());
    }
    
    /**
    / This method optionally sets up logging
	**/
	private void setupLogging(String mode) 
	{
		// see if the USB stick is plugged into to RoboRIO
		Path path = Paths.get(RobotMap.LOG_FILE_PATH);
    	if (Files.exists(path)) 
    	{
    		try 
    		{
				_dataLogger = new DataLogger(RobotMap.LOG_FILE_PATH, mode);
				_dataLogger.WriteData(_robotLiveData.BuildTSVHeader());
				
				_robotLiveData.WorkingDataValues.LogFilePathName = _dataLogger.LogFilePathName;
	    		_robotLiveData.WorkingDataValues.IsLoggingEnabled = true;
	    		_robotLiveData.WorkingDataValues.LoggingStartedDT = new Date();
	    		_robotLiveData.WorkingDataValues.LastScanDT = new Date();
			} 
    		catch (IOException e) 
    		{
				e.printStackTrace();
				
	    		_dataLogger = null;
				_robotLiveData.WorkingDataValues.LogFilePathName = "";
	    		_robotLiveData.WorkingDataValues.IsLoggingEnabled = false;
			}
    	}
    	else
    	{
    		_dataLogger = null;
			_robotLiveData.WorkingDataValues.LogFilePathName = "";
    		_robotLiveData.WorkingDataValues.IsLoggingEnabled = false;
    	}
	}
	
	public void testInit() {
		
		try {
			_visionServer = new Socket(RobotMap.VISION_PC_IP_ADDRESS, RobotMap.VISION_PC_PORT);
			DriverStation.reportError("Connection to Vision PC successful", false);
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			DriverStation.reportError("Connection to Vision PC has failed", false);
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			DriverStation.reportError("Connection to Vision PC has failed", false);
			e.printStackTrace();
		}
    
	}
	
    /*****************************************************************************************************
     * This function is called periodically during test mode
     * 	(about 50x/sec or about every 20 mSec)
     *****************************************************************************************************/
    public void testPeriodic()
    {
    	InputData inputDataValues = _robotLiveData.InputDataValues;
    	WorkingData workingDataValues = _robotLiveData.WorkingDataValues;
    	OutputData outputDataValues = _robotLiveData.OutputDataValues;
    	
    	InputStream inFromServer;
    	String visionData = "";
    	
    	if (inputDataValues.IsPrintDataBtnPressed)
    	{
    		
			try {
				inFromServer = _visionServer.getInputStream();
				 DataInputStream in =
	                     new DataInputStream(inFromServer);
				 visionData= in.readUTF();
				 System.out.println("Server says " + visionData);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
    	else 
    	{
    	}
    	
    
		String delims = "|";
		String[] splitVisionData = visionData.split(delims);
		
		if (splitVisionData.length == 6){
			inputDataValues.IsValidData = Boolean.parseBoolean(splitVisionData[RobotMap.IS_VALID_DATA_ARRAY_POSITION]);
			inputDataValues.DistanceToTarget = Double.parseDouble(splitVisionData[RobotMap.DISTANCE_TO_TARGET_ARRAY_POSITION]);
			inputDataValues.EffectiveTargetWidth = Double.parseDouble(splitVisionData[RobotMap.EFFECTIVE_TARGET_WIDTH_ARRAY_POSITION]);
			inputDataValues.DesiredSliderPosition = Double.parseDouble(splitVisionData[RobotMap.DESIRED_SLIDER_POSITION_ARRAY_POSITION]);
			inputDataValues.DesiredTurretTurnInDegrees = Double.parseDouble(splitVisionData[RobotMap.DESIRED_TURRET_TURN_IN_DEGREES_ARRAY_POSITION]);
			inputDataValues.IsValidShot = Boolean.parseBoolean(splitVisionData[RobotMap.IS_VALID_SHOT_ARRAY_POSITION]);
			DriverStation.reportError(visionData, false);
		}
		else 
		{
			DriverStation.reportError("Did not recieve correct vision data", false);
			DriverStation.reportError(visionData, false);
		}
    	//LiveWindow.run();
    }
    
}

