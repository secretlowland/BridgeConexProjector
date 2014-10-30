package com.bridgeface.projector;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * MainActivity. 与服务器连接后接受数据的视图
 */
public class MainActivity extends Activity {

	private Bundle bundle;
	private InputStream is;
	private DataInputStream dis;
	private FileOutputStream fos;
	private Socket socket;
	private ServerSocket serverSocket = null;
	private NetHandler netHandler;
	private NetThread netThread;
	private Handler countDownHandler;
	private Handler getImageHandler;
	private Handler refreshIpHandler;
	private Handler exceptionHander;
	private Timer countDownTimer; // 倒计时定时器
	private Timer getImageTimer;
	private TimerTask countDownTask;
	private TimerTask getImageTask;

	// 申明初始化界面布局控件
	private LinearLayout initLayout;
	private TextView screenSize;
	private TextView ipAddress;
	private TextView connectStatus;
	private ProgressBar connect_progress;

	// 申明显示图片界面控件
	private LinearLayout showImageLayout;
	private ImageView img;

	// 申明倒计时界面控件
	private LinearLayout countDownLayout;
	private TextView leftTime;
	private TextView gameName;

	private boolean thread_flag = true;
	private boolean show_img_flag = true;
	private boolean show_time_flag = false;

	private int get_ip_time = 0;
	private int data_length = 0;
	private int img_num = 0;
	private int img_counter = 0;
	private int interval = 5000;
	private long exit_time = 0;
	private byte[] data_str = null; // 存放字符串
	private byte[] data_bin = null; // 存放二进制文件（图片）

	/** 重写 onCreate() 函数 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// 初始化变量
		initVariable();

		if (!netThread.isAlive()) {
			netThread.start();
		}
		refreshLocalIp();
		showScreenSize();
		// 显示图片
		startImageLoop(interval);

		exceptionHander = new Handler() {

			@Override
			public void handleMessage(Message msg) {
				Bundle b = msg.getData();
				String data_tran_ecp = b.getString("data_tran_ecp");
				Toast.makeText(getApplicationContext(), data_tran_ecp,
						Toast.LENGTH_LONG).show();
				super.handleMessage(msg);
			}

		};

	}

	@Override
	protected void onDestroy() {
		// 善后工作
		try {

			thread_flag = false; // 将线程停止标记记为false，以便停止线程

			if (!serverSocket.isClosed()) {
				serverSocket.close(); // 关闭 serverSocket
			}

			// 关闭显示图片的线程
			if (getImageTimer != null) {
				getImageTimer.cancel();
			}

			// 关闭显示倒计时的线程
			if (countDownTimer != null) {
				countDownTimer.cancel();
			}
			
			//删除图片
			deleteImage();

		} catch (IOException e) {
			e.printStackTrace();
		}
		super.onDestroy();
	}

	/**
	 * 按返回键时提醒用户是否退出，并做出相应善后工作
	 */

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {

		if (keyCode == KeyEvent.KEYCODE_BACK) { // 点击返回按钮

			if ((System.currentTimeMillis() - exit_time) > 2000) { // 连续点击返回时间间隔小于
																	// 2 秒

				Toast.makeText(this, "再按一次退出", Toast.LENGTH_SHORT).show();
				exit_time = System.currentTimeMillis();
				System.out.println("请再点击一次");
			} else {
				finish();
			}
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public void onBackPressed() {
		// Do nothing
		// super.onBackPressed(); 父类方法会直接调用 finish() 方法
	}

	public void initVariable() {

		// 初始化初始界面控件
		initLayout = (LinearLayout) findViewById(R.id.layout_init);
		screenSize = (TextView) findViewById(R.id.tv_screen_size);
		ipAddress = (TextView) findViewById(R.id.tv_ip_address);
		connectStatus = (TextView) findViewById(R.id.tv_connect_status);

		// 初始化显示图片界面控件
		showImageLayout = (LinearLayout) findViewById(R.id.layout_show_image);
		img = (ImageView) findViewById(R.id.iv_img);

		// 初始化倒计时界面控件
		countDownLayout = (LinearLayout) findViewById(R.id.layout_count_down);
		leftTime = (TextView) findViewById(R.id.tv_left_time);
		gameName = (TextView) findViewById(R.id.tv_game_name);

		countDownTimer = new Timer();
		netHandler = new NetHandler();
		bundle = new Bundle();
		netThread = new NetThread();

	}

	/**
	 * 显示屏幕大小
	 */
	public void showScreenSize() {
		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);

		int width = metrics.widthPixels;
		int height = metrics.heightPixels;
		System.out.println(metrics.toString());
		screenSize.setText("可显示区域：" + width + "X" + height);
		System.out.println("width-->" + width + "; height-->" + height);
	}

	/**
	 * 获取本机 IP
	 */
	public String getLocalIp() {

		get_ip_time++;
		// 获取 wifi 服务
		WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

		// 判断 wifi 是否开启
		if (!wifiManager.isWifiEnabled()) {
			if (get_ip_time == 1) // 判断是第一次提示开启wifi
				wifiManager.setWifiEnabled(true); // 开启 wifi
		}

		int ipAddress = wifiManager.getConnectionInfo().getIpAddress(); // 得到的是 int 类型

		if (ipAddress > 0) {
			return ("IP地址 : " + ipToString(ipAddress) + ":7611");
		} else {
			if (get_ip_time < 8) {
				return "IP地址：正在获取...";
			} else {
				return "IP地址 : 不可用";
			}
		}

		// is_first_get_ip = false;
	}

	/**
	 * 自动监测 IP 并更新 IP 状态
	 */
	public void refreshLocalIp() {

		boolean isFirstTime = true;

		// 自动监测 IP 每 3秒监测一次
		refreshIpHandler = new Handler() {

			@Override
			public void handleMessage(Message msg) {
				switch (msg.what) {
				case 0:

					ipAddress.setText(getLocalIp());
					if (getLocalIp().equals("IP地址 : 不可用")) {
						connectStatus.setText("正在检测网络环境  ");
					} else if (getLocalIp().equals("IP地址：正在获取...")) {
						connectStatus.setText("正在配置网络环境  ");
					} else {
						connectStatus.setText("正在等待接收数据  ");
					}
					break;
				}
				super.handleMessage(msg);
			}

		};

		new Timer().schedule(new TimerTask() {

			@Override
			public void run() {
				Message msg = new Message();
				msg.what = 0;
				refreshIpHandler.sendMessage(msg);
			}

		}, 5000, 3000);

	}

	/**
	 * 将 int 类型 ip 转换成 String 类型
	 */
	public String ipToString(int i) {
		return (i & 0xff) + "." + ((i >> 8) & 0xff) + "." + ((i >> 16) & 0xff)
				+ "." + ((i >> 24) & 0xff);
	}

	public void startImageLoop(int interval) {

		getImageHandler = new Handler() {

			public void handleMessage(Message msg) {
				switch (msg.what) {
				case 1:

					System.out.println("开始一次循环");
					
					// 获取外部存储目录
					File fl = getExternalCacheDir();
					File imgFile = null;
					String filePath = null;
					String firstFilePath = null;
					
					if (checkSDCard(fl)) {
						firstFilePath = fl.toString() + "/image0.jpg";
						filePath = fl.toString() + "/image" + (img_counter++)
								+ ".jpg";
						imgFile = new File(filePath);
						if (img != null && imgFile.exists()) {
							showImage(filePath);
						} else {
							if (show_time_flag) {
								img_counter = 0;
								//显示倒计时
								clearScreen();
								countDownLayout.setVisibility(View.FOCUS_FORWARD);
							} else {
								if(new File(firstFilePath).exists()) {
									img_counter = 1;
									showImage(firstFilePath);
								} else {
									//显示初始化界面
									clearScreen();
									initLayout.setVisibility(View.FOCUS_BACKWARD);
									//show_time_flag = false;
								}
							}
						}

					} else {
						System.out.println("sd卡读取失败！");
					}

					break;

				}
				super.handleMessage(msg);
			}
		};
		getImageTask = new TimerTask() {
			public void run() {
				Message msg = new Message();
				msg.what = 1;
				getImageHandler.sendMessage(msg);
			}
		};
		getImageTimer = new Timer(true);
		getImageTimer.schedule(getImageTask, 0, interval);
	}

	public void stopImageLoop() {
		//
	}

	/** 显示指定路径下的图片 */
	public void showImage(String file) {

		clearScreen();
		showImageLayout.setVisibility(View.FOCUS_BACKWARD); // 让图片控件处于可显示状态
		Bitmap bitmap = BitmapFactory.decodeFile(file); // 从本地取图片
		img.setImageBitmap(bitmap); // 设置图片
		System.out.println("显示图片----->" + file);

		// // 获取图片路径
		// File fl = MainActivity.this.getExternalCacheDir();
		// checkSDCard(fl);
		// String filePath = fl.toString() + "/image"
		// + (img_counter++) + ".jpg";
		// File file = new File(filePath);
		//
		// // 判断文件是否存在
		// if (file.exists()) {
		// clearScreen();
		// showImageLayout.setVisibility(View.FOCUS_BACKWARD); // 让图片控件处于可显示状态
		//
		// } else {
		//
		// img_counter = 0; //图片计数器归零
		//
		// //从第一张开始显示
		// String firstFile = fl.toString() + "/image"
		// + (img_counter++) + ".jpg";
		// if (new File(firstFile).exists()) {
		// clearScreen();
		// showImageLayout.setVisibility(View.FOCUS_BACKWARD); // 让图片控件处于可显示状态
		// Bitmap bitmap = BitmapFactory
		// .decodeFile(firstFile); // 从本地取图片
		// img.setImageBitmap(bitmap); // 设置图片
		// System.out.println("从第一张开始显示");
		// System.out.println("显示图片----->" + firstFile);

		// loading_progress.setVisibility(View.GONE); // 去除加载图片进度
		// img.setVisibility(View.FOCUS_BACKWARD); // 让图片控件处于可显示状态
		// //显示倒计时界面
		// showImageLayout.setVisibility(View.GONE); // 让图片控件处于不可显示状态
		// countDownLayout.setVisibility(View.FOCUS_BACKWARD);
		// }
		// }

	}

	/** 清除主界面所有控件内容 （隐藏界面内容） */
	public void clearScreen() {

		initLayout.setVisibility(View.GONE);
		showImageLayout.setVisibility(View.GONE);
		countDownLayout.setVisibility(View.GONE);

	}

	/**
	 * 检查SD是否读取正确，如果不正确，则弹出读取SD卡失败的 Toast
	 * 
	 * @param fl
	 */
	public boolean checkSDCard(File fl) {
		if (fl == null) {
			Toast.makeText(this, "读取存储卡失败！", Toast.LENGTH_SHORT).show();
			return false;
		} else {
			return true;
		}
	}
	
	
	/** 删除已经存储的除了 temp.jpg 的所有图片 */
	public void deleteImage() {
		
		show_time_flag = false;

		int i = 0;
		File fl = MainActivity.this.getExternalCacheDir();
		boolean flag = true;
		while (flag) {
			String filePath = fl.toString() + "/image" + (i++) + ".jpg";
			File file = new File(filePath);
			// 判断文件是否存在
			if (file.exists()) {
				file.delete();
				System.out.println("删除图片---->" + filePath);
			} else {
				System.out.println("File to delete not found!");
				img_num = 0;
				flag = false;
			}
		}
	}
	

	/**
	 * 内部类.NetThread 用于进行网络连接的线程。Android 3.0 以后与网络相关的操作不能 在主线程中
	 */

	class NetThread extends Thread {

		/** 线程启动后调用该方法。Thread 类必须重写 run 方法 */
		@Override
		public void run() {

			// 监听端口。这部分不能放在 connect() 函数里面一起循环，否则第二次连接不上
			if (serverSocket == null) {
				try {
					serverSocket = new ServerSocket(7611);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			// 第一层循环，循环接收来自服务端发来的数据包
			while (thread_flag) {
				if (connect()) {
					receive();
				} else {
					break;
				}
			}

		}

		/** 连接服务端 */
		public boolean connect() {
			try {

				socket = serverSocket.accept(); // 等待服务端连接，此处阻塞
				System.out.println("连接成功");
				is = socket.getInputStream();
				dis = new DataInputStream(is);
				return true;
			} catch (IOException e) {
				System.out.println("连接失败");
				e.printStackTrace();
				return false;
			}
		}

		/** 接收服务端发来数据（每发送一次就接收一次） */
		public void receive() {

			// 第二层循环，如果服务端一次发送了多张图片（多个数据包粘连在一起）则循环接收
			// 粘在一起的数据包
			while (true) {
				if ((data_length = getDataLen(dis)) > 0) { // 判断 dis流中是否有数据
					System.out.println("===========开始接收数据=============");
					receiveData(); // 接收数据
					System.out.println("接收数据成功");
					try {
						handleData(); // 处理数据
						System.out.println("处理数据成功");
						sendData(); // 发送数据
						System.out.println("发送数据成功");
						System.out.println("============我是快乐的分割线===========");
						Thread.sleep(50); // 休眠50ms
					} catch (Exception e) {
						Bundle exceptionBundle = new Bundle();
						exceptionBundle.putString("data_tran_ecp", "数据传输异常！");
						Message msg = new Message();
						msg.setData(exceptionBundle);
						MainActivity.this.exceptionHander.sendMessage(msg);
						e.printStackTrace();
					}
				} else {
					System.out.println("继续监听");
					break;
				}
			}
		}

		/** 从服务端接收带包头的数据包,如果接收到了数据则返回 true,否则返回 false */
		public void receiveData() {

			System.out.println("数据包长度为：" + data_length);

			getData(); // 获取数据

			try {
				String str = new String(data_str, "unicode"); // 将字符串转换成 unicode
																// 文本
				System.out.println("字符串长度：" + str.length());
				System.out.println("接收的字符串消息为：");
				System.out.println(str); // 输出字符串
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		}

		/** 获取数据包的长度并返回，没有数据则返回 -1 */
		public int getDataLen(DataInputStream dis) {

			int data_len = 0; // 数据包的长度
			byte[] b = new byte[4]; // 存放数据包长度的数据流

			try {
				Thread.sleep(0);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}

			try {
				int i = dis.read(b);
				if (i != -1) {
					data_len = byteArrayToInt(b, 0);
				} else {
					data_len = -1;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

			return data_len;
		}

		/** 获取数据 */
		public void getData() {

			// 线程休眠一段时间,等待接受全部数据包,相当于缓冲作用
			try {
				Thread.sleep(0);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}

			try {

				// byteFlag不能直接放在 if中，不然readByte()方法会读2次
				byte byteFlag = dis.readByte();

				// 数据为纯文本
				if (byteFlag == 0x01) {
					System.out.println("数据为纯文本");
					data_str = new byte[data_length - 5];
					dis.read(data_str); // 把字符串读入 data_str 数组
				}

				// 数据为文本 + 二进制数据流
				else if (byteFlag == 0x05) {
					System.out.println("数据为图片和文本");

					try {
						File fl = MainActivity.this.getExternalCacheDir();
						checkSDCard(fl); // 检查存储卡
						String filePath = fl.toString() + "/temp.jpg"; // 临时存储图片路径
						System.out.println("存入临时文件：" + filePath);
						fos = new FileOutputStream(filePath); // 文件输出流
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					}

					byte[] b2 = new byte[4]; // 存放二进制文件的长度
					dis.read(b2);
					int data_bin_len = byteArrayToInt(b2, 0); // 计算二进制文件的长度
					int length = data_bin_len;
					System.out.println("图片的大小---->" + length);
					data_bin = new byte[data_bin_len]; // 这个暂时不用

					readImageData(dis, data_bin, length);
					writeImageData(fos, data_bin, length);

					// int bufSize = 1024;
					// byte[] buffer = new byte[bufSize];
					//
					// // 把图片的二进制数据存入内存
					// // 存入文件的时候要一点一点的存，特别是图片。不然数据会损坏。最好以1024以下的大小为一个单位
					// while (length > bufSize) {
					// dis.read(buffer);
					// fos.write(buffer);
					// length -= bufSize;
					// }
					// dis.read(data_bin, 0, length); // 把二进制文件读入 data_bin 数组
					// fos.write(data_bin, 0, length); // 把二进制文件写入文件输出流
					// fos.close(); // 关闭文件输出流

					int data_str_len = (data_length - data_bin_len - 9);
					System.out.println("字符串大小data_str_len:" + data_str_len);
					data_str = new byte[data_str_len];
					dis.read(data_str); // 把字符串读入 data_str 数组

				} else {
					System.out.println("数据传输发生错误！"); // 数据类型错误，此处应该是传输问题
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		/**
		 * 从输入流中读取图片数据
		 * 
		 * @param dis
		 * @param data_bin
		 * @param length
		 */
		public void readImageData(DataInputStream dis, byte[] data_bin,
				int length) {

			int bufSize = 8;
			int curr_len = 0;
			byte[] buf = new byte[bufSize];

			while (curr_len < (length - bufSize)) {
				try {
					dis.read(data_bin, curr_len, bufSize); // 把二进制文件读入 data_bin
															// 数组
					curr_len += bufSize;
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			// 读取剩余部分
			try {
				dis.read(data_bin, curr_len, (length - curr_len));
			} catch (IOException e) {
				e.printStackTrace();
			}

		}

		/**
		 * 把图片数据写入文件流中
		 * 
		 * @param fos
		 * @param data
		 * @param length
		 */
		public void writeImageData(FileOutputStream fos, byte[] data, int length) {
			try {
				fos.write(data);
				fos.close();
			} catch (IOException e) {
				System.out.println("写入数据失败！");
				e.printStackTrace();
			}
		}

		/** 处理接收后的数据 */
		public void handleData() throws Exception {

			// 获取消息头
			StringBuilder msg = new StringBuilder(); // 数据包消息头，存放消息类型
			String str = null;

			try {
				str = new String(data_str, "unicode"); // 将字节数组转化成 unicode 字符串
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}

			int i = 0;
			// 获取数据包的消息头 msg
			// 此处的 '#' 不能用换行字符，换行字符不能被 charAt()方法识别
			while (str.charAt(i) != '#' && i < 8) {
				msg.append(str.charAt(i));
				i++;
			}
			msg.append(str.charAt(i++));
			msg.append(str.charAt(i++));
			msg.append(str.charAt(i++));

			// 对消息头进行判断
			if (msg.toString().equals("ya_M##1")) {

				// 接收的消息为 IDCtip_msg_ImageToProjector 图片发送到投影仪
				System.out.println("消息头为：" + msg.toString());
				String[] msgs = str.split("\n");

				bundle.putString("ya_M", msgs[0]);
				bundle.putString("FlashSpan", msgs[1]);
				bundle.putString("ClearScreen", msgs[2]);
				bundle.putString("FirstPage", msgs[3]);

			} else if (msg.toString().equals("ya_M##2")) {

				// 接收的消息为 IDCtip_msg_ClearProjector 清空投影消息
				System.out.println("消息头为：" + msg.toString());
				bundle.putString("ya_M", "ya_M##2");

			} else if (msg.toString().equals("ya_M##3")) {

				// 接收的消息为 IDCtip_msg_CountDown 倒计时消息
				System.out.println("消息头为：" + msg.toString());
				String[] msgs = str.split("\n");

				bundle.putString("ya_M", msgs[0]);
				bundle.putString("ya_Event", msgs[1]);
				bundle.putString("endTime_rs", msgs[2]);
				bundle.putString("_MapTime", msgs[3]);
				bundle.putString("FlashSpan", msgs[4]);
				bundle.putString("ClearScreen", msgs[5]);

			} else {

				// 偶尔会出现这个问题，应该是数据传输错误。此处应该采取相应措施，否则程序会崩溃。
				System.out.println("数据传输异常");
				throw new Exception("数据传输异常啦o(>﹏<)o");

			}

		}

		/** 把数据通过 Message 送到 Handler */
		public void sendData() {

			Message msg = new Message();
			msg.setData(bundle);
			MainActivity.this.netHandler.sendMessage(msg);

		}

		/** 将 byte[] 转换成 int 类型 */
		public int byteArrayToInt(byte[] b, int offset) {
			int value = 0;
			for (int i = 3; i >= 0; --i) {
				value <<= 8;
				value += (int) (b[i] & 0xff);
			}
			return value;
		}

	}

	/**
	 * 内部类.消息处理类
	 */
	class NetHandler extends Handler {

		/** 无参构造函数 */
		public NetHandler() {
		}

		/** 对消息进行处理。该方法必须重写 */
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);

			// 此处可以操作 UI
			Bundle b = msg.getData(); // 获取NetThread发送过来的消息
			String ya_M = b.getString("ya_M").trim();

			// 消息为图片发送到投影仪
			if (ya_M != null && ya_M.equals("ya_M##1")) {

				// 接收其他消息
				String FlashSpan = b.getString("FlashSpan").trim();
				String ClearScreen = b.getString("ClearScreen").trim();
				String FirstPage = b.getString("FirstPage").trim();

				int interval = 1000 * Integer.parseInt(FlashSpan.substring(11));
				System.out.println("图片显示间隔时间----------〉" + interval);


				// 判断是否清屏（删除图片）
				if (ClearScreen != null && ClearScreen.equals("ClearScreen##1")) {
					deleteImage(); // 删除图片
				}

				// 保存图片
				saveImage();

			}

			// 消息为清空投影仪显示
			if (ya_M != null && ya_M.equals("ya_M##2")) {

				// 清空投影仪
				clearScreen();

				// 删除所有存储的图片
				deleteImage();
				
				// 取消倒计时线程
				if (countDownTimer != null) {
					countDownTimer.cancel();
				}

				// 初始化界面，让主界面界面控件显示出来
				initLayout.setVisibility(View.FOCUS_BACKWARD);
			}

			// 消息为显示倒计时
			if (ya_M != null && ya_M.equals("ya_M##3")) {

				// 接收其他消息
				String ya_Event = b.getString("ya_Event").trim();
				String endTime_rs = b.getString("endTime_rs").trim();
				String _MapTime = b.getString("_MapTime").trim();
				String flashSpan = b.getString("FlashSpan").trim();
				String ClearScreen = b.getString("ClearScreen").trim();

				//clearScreen();

				// 判断是否清屏（删除图片）
				if (ClearScreen != null && ClearScreen.equals("ClearScreen##1")) {
					deleteImage(); // 删除图片
				}
				
				// 取消之前的倒计时线程
				if (countDownTimer != null) {
					countDownTimer.cancel();
				}

				// 显示倒计时界面
				//countDownLayout.setVisibility(View.FOCUS_FORWARD);
				show_time_flag = true;

				// 显示比赛名称
				gameName.setText(ya_Event.substring(10));

				// 获取结束时间，用byte数组存放
				int end_time[] = new int[6];
				for (int i = 12; i < endTime_rs.length(); i++) {
					char c = endTime_rs.charAt(i);
					end_time[i - 12] = (int) c - 48;
					System.out.print(end_time[i - 12] + ",");
				}

				System.out.println();
				// 获取服务器时间，用byte数组存放
				int map_time[] = new int[6];
				for (int i = 10; i < _MapTime.length(); i++) {
					char c = _MapTime.charAt(i);
					map_time[i - 10] = (int) c - 48;
					System.out.print(map_time[i - 10] + ",");
				}
				System.out.println();

				int year = end_time[5] - map_time[5];
				int mon = end_time[4] - map_time[4];
				int day = end_time[3] - map_time[3];
				int hour = end_time[2] - map_time[2];
				int min = end_time[1] - map_time[1];
				int sec = end_time[0] - map_time[0];

				int sec_total = (sec + min * 60 + hour * 3600 + day * 86400);
				leftTime.setVisibility(View.FOCUS_BACKWARD);
				leftTime.setText(sec_total + "");

				countDownHandler = new Handler() {
					String str = leftTime.getText().toString(); // 从 TextView
					// 获取总共剩余时间
					int sec_total = Integer.parseInt(str); // 将字符串转化成 int

					public void handleMessage(Message msg) {
						sec_total--;
						switch (msg.what) {
						case 1:
							leftTime.setText(formatTime(sec_total));
							break;
						}
						super.handleMessage(msg);
					}
				};
				countDownTask = new TimerTask() {
					public void run() {
						Message msg = new Message();
						msg.what = 1;
						countDownHandler.sendMessage(msg);
					}
				};
				countDownTimer = new Timer(true);
				countDownTimer.schedule(countDownTask, 0, 1000);
			}
		}

		/** 保存图片 */
		public void saveImage() {

			String tempFilePath = MainActivity.this.getExternalCacheDir()
					.toString() + "/temp.jpg"; // 获取临时存储图片路径
			String newFilePath = MainActivity.this.getExternalCacheDir()
					.toString() + "/image" + (img_num++) + ".jpg"; // 重新定义图片路径
			File tempFile = new File(tempFilePath);
			File newFile = new File(newFilePath);
			tempFile.renameTo(newFile); // 对临时图片进行重命名
			System.out.println("保存图片----->" + newFilePath);
		}


		/** 将时间（秒）转换成 MM:SS 格式 */
		public String formatTime(int seconds) {

			String time = null;
			String mm, ss;
			int m = seconds / 60;
			int s = (seconds - m * 60);

			if (Math.abs(m) < 10) {
				mm = "0" + Math.abs(m);
			} else {
				mm = "" + Math.abs(m);
			}

			if (Math.abs(s) < 10) {
				ss = "0" + Math.abs(s);
			} else {
				ss = "" + Math.abs(s);
			}

			if (seconds >= 0) { // 总时间为正数
				time = mm + ":" + ss;
			} else { // 总时间为负数
				time = "-" + mm + ":" + ss;
			}
			return time;
		}
	}

}
