import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.smartcardio. * ;

import org.json.JSONObject;

import com.sun.org.apache.xalan.internal.xsltc.runtime.Parameter;
import java.util.Objects;
import org.json.JSONException;

public class CardReader {

	private static final String SAMPLE_LOYALTY_CARD_AID = "F222222222";
	private static final String SELECT_APDU_HEADER = "00A40400";
	private static final String GET_BALANCE_APDU_HEADER = "805A0000";
	private static final String DEBIT_APDU_HEADER = "80540000";
	private static final String DEPOSIT_APDU_HEADER = "80550000";
	private static final String GET_KEY_APDU_HEADER = "80560000";
	private static final String SET_KEY_APDU_HEADER = "80570000";
	private static final String GET_EMAIL_APDU_HEADER = "80580000";
	private static final byte[] SELECT_OK_SW = { (byte) 0x90,
		(byte) 0x00
	};
	private Boolean resultStatus = false;
	private String result;
	private int dollar = 0;
	private String cardAID,
	apduHeader,
	insertKey;

	public CardReader(String CardAID, String ApduHeader,int dollar, Boolean isDeposit, Boolean isDebit) throws JSONException {
		setResultStatus(InitializeConnection(CardAID));
		if (getResultStatus() == true) {
			byte[] CommandBytes = BuildAIDApdu(CardAID, ApduHeader);
			ExecuteTask(CommandBytes);
                        if (ApduHeader == GET_EMAIL_APDU_HEADER){
                            if (isDeposit == false && isDebit == false){
                                String email = getResult();
                                JSONObject tempTicket = GetUserTempTicketPriceAndKey(email);
                                String dollarString = IntToByteString(tempTicket.getInt("price"));
                                String ticketKey = String.valueOf(tempTicket.getString("key"));
                                byte[] CommandBytesForDebit = BuildParameterApdu(DEBIT_APDU_HEADER, dollarString);
                                ExecuteTask(CommandBytesForDebit);
                                if(getResultStatus() == true){
                                    Boolean transferResult = TransferTicketDataToFormalTable(email);
                                    if (transferResult == true){
                                        String Parameter = toHex(ticketKey);
                                        byte[] CommandBytesForSetKey = BuildParameterApdu(SET_KEY_APDU_HEADER, Parameter);
                                        ExecuteTask(CommandBytesForSetKey);
                                    }
                                }
                            }else {
                                String email = getResult();
                                String dollarString = IntToByteString(1);
                                byte[] CommandBytess = null;
                                if (isDeposit == true){
                                    CommandBytess = BuildParameterApdu(DEPOSIT_APDU_HEADER, dollarString);
                                    Deposit(email, dollar);
                                }
                                if (isDebit == true){
                                    CommandBytess = BuildParameterApdu(DEBIT_APDU_HEADER, dollarString);
                                    Debit(email, dollar);
                                }
                                ExecuteTask(CommandBytess);
                                setResultStatus(true);
                            }
                        }
                     System.out.println("--------------------------------------------------");
		}
	}

	public CardReader(String CardAID, String ApduHeader, int Dollar, Boolean GetKeyMode) {
		setResultStatus(InitializeConnection(CardAID));
		if (getResultStatus() == true) {
			String dollarString = IntToByteString(Dollar);
                     if (GetKeyMode == false){
                            byte[] CommandBytes = BuildParameterApdu(ApduHeader, dollarString);
                            ExecuteTask(CommandBytes);
                            byte[] CommandBytesForSelection = BuildAIDApdu(CardAID, GET_BALANCE_APDU_HEADER);
                            ExecuteTask(CommandBytesForSelection);
                     }else if (GetKeyMode == true){
                            byte[] CommandBytesForSelection = BuildParameterApdu(ApduHeader, dollarString);
                            ExecuteTask(CommandBytesForSelection);
                     }
                     System.out.println("--------------------------------------------------");
		}
	}

	public CardReader(String CardAID, String ApduHeader, String InsertKey) {
		setResultStatus(InitializeConnection(CardAID));
		if (resultStatus == true) {
			String Parameter = toHex(InsertKey);
			byte[] CommandBytes = BuildParameterApdu(ApduHeader, Parameter);
			ExecuteTask(CommandBytes);
                     System.out.println("--------------------------------------------------");
		}
	}

	public Boolean InitializeConnection(String CardAID) {
		System.out.println("【Selection Mode】");
		System.out.println("開始建立卡片與讀卡機的連線…");
		TerminalFactory terminalFactory = TerminalFactory.getDefault();

		try {
			for (CardTerminal terminal: terminalFactory.terminals().list()) {
				System.out.println("讀卡機名稱: " + terminal.getName());
				try {
					Card card = terminal.connect("*");
					CardChannel channel = card.getBasicChannel();
					byte[] commandBytes = BuildSelectApdu(CardAID);
					CommandAPDU command = new CommandAPDU(commandBytes);
					System.out.println("送出APDU: " + ByteArrayToHexString(commandBytes));
					ResponseAPDU response = channel.transmit(command);
					byte recv[] = response.getBytes();
					System.out.println("回傳結果: " + ByteArrayToHexString(recv));
					int resultLength = recv.length;

					byte[] statusWord = {
						recv[resultLength - 2],
						recv[resultLength - 1]
					};
					if (Arrays.equals(SELECT_OK_SW, statusWord)) {
						System.out.println("連線成功！");
						resultStatus = true;
						System.out.println("【End Select Mode】");
					}

				} catch(javax.smartcardio.CardNotPresentException e) {
					// e.printStackTrace();
					continue;
				} catch(CardException e) {
					// e.printStackTrace();
					continue;
				}
			}
		} catch(NumberFormatException e) {
			e.printStackTrace();
		} catch(CardException e) {
			e.printStackTrace();
		}
		return resultStatus;
	}

	public void ExecuteTask(byte[] CommandBytes) {
		TerminalFactory terminalFactory = TerminalFactory.getDefault();
		System.out.println("【Mode】");
		System.out.println("開始建立卡片與讀卡機的連線…");
		try {
			for (CardTerminal terminal: terminalFactory.terminals().list()) {
				try {
					Card card = terminal.connect("*");
					CardChannel channel = card.getBasicChannel();

					CommandAPDU command = new CommandAPDU(CommandBytes);
					// Send command to remote device
					System.out.println("送出APDU: " + ByteArrayToHexString(CommandBytes));
					ResponseAPDU response = channel.transmit(command);
					byte recv[] = response.getBytes();
					System.out.println("回傳結果: " + ByteArrayToHexString(recv));
					int resultLength = recv.length;
					byte[] statusWord = {
						recv[resultLength - 2],
						recv[resultLength - 1]
					};
					byte[] payload = Arrays.copyOf(recv, resultLength - 2);
					if (Arrays.equals(SELECT_OK_SW, statusWord)) {

						System.out.println("送出成功！");
						setResult(new String(payload, "UTF-8"));
						if (getResult() != null) {
							resultStatus = true;
						}
						System.out.println("【End Mode】");
					}

				} catch(javax.smartcardio.CardNotPresentException e) {
					// e.printStackTrace();
					continue;
				} catch(CardException e) {
					// e.printStackTrace();
					continue;
				} catch(UnsupportedEncodingException e) {
					// TODO 自動產生的 catch 區塊
					e.printStackTrace();
				}
			}
		} catch(NumberFormatException e) {
			e.printStackTrace();
		} catch(CardException e) {
			e.printStackTrace();
		}
	}

	public static byte[] BuildSelectApdu(String AID) {
		return HexStringToByteArray(SELECT_APDU_HEADER + String.format("%02X", AID.length() / 2) + AID);
	}

	public static byte[] BuildAIDApdu(String AID, String ApduHeader) {
		return HexStringToByteArray(ApduHeader + String.format("%02X", AID.length() / 2) + AID);
		//          return HexStringToByteArray("00A40400" + "05" + "F222222222"); //連線
		//          return HexStringToByteArray("805A0000" + "05" + "F222222222"); //取餘額
		//          return HexStringToByteArray("80560000" + "05" + "F222222222"); //取ＫＥＹ
	}

	public static byte[] BuildParameterApdu(String ApduHeader, String Parameter) {
		return HexStringToByteArray(ApduHeader + String.format("%02X", Parameter.length() / 2) + Parameter);
		//          return HexStringToByteArray("80540000" + "02" + "3132"); //減12元(十六進位) //請先取餘額判斷會不會超過
		//          return HexStringToByteArray("80550000" + "01" + "39"); //加9元(十六進位)
		//          return HexStringToByteArray("80570000" + "05" + "33256f5040"); //輸入ＫＥＹ(十六進位) 
	}

	/**
	 * Utility class to convert a byte array to a hexadecimal string.
	 *
	 * @param bytes Bytes to convert
	 * @return String, containing hexadecimal representation.
	 */
	public static String ByteArrayToHexString(byte[] bytes) {
		final char[] hexArray = {
			'0',
			'1',
			'2',
			'3',
			'4',
			'5',
			'6',
			'7',
			'8',
			'9',
			'A',
			'B',
			'C',
			'D',
			'E',
			'F'
		};
		char[] hexChars = new char[bytes.length * 2];
		int v;
		for (int j = 0; j < bytes.length; j++) {
			v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

        public String StringToByteString(String s){
           byte b[] = s.getBytes();
           String t = "";
           try {
                t = new String(b);
                System.out.print(t);
            } catch (Exception e) {
                e.printStackTrace();
           }
           return t;
        }
        
	public static String IntToByteString(int dollar) {
		String tempString = String.valueOf(dollar);
		char[] stringToCharArray = tempString.toCharArray();
		String[] byteStringArray = {
			"30",
			"31",
			"32",
			"33",
			"34",
			"35",
			"36",
			"37",
			"38",
			"39"
		};
		String dollarByteString = "";
		for (char output: stringToCharArray) {
			dollarByteString += byteStringArray[Character.getNumericValue(output)];
		}
		return dollarByteString;
	}

	/**
	 * Utility class to convert a hexadecimal string to a byte string.
	 *
	 * <p>Behavior with input strings containing non-hexadecimal characters is undefined.
	 *
	 * @param s String containing hexadecimal characters to convert
	 * @return Byte array generated from input
	 */
	public static byte[] HexStringToByteArray(String s) {
		int len = s.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte)((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
		}
		return data;
	}
        
       public static String toHex(String s){
            byte[] ba = s.getBytes();
            StringBuilder str = new StringBuilder();
            for(int i = 0; i < ba.length; i++){
                str.append(String.format("%02x", ba[i]));
            }
            return str.toString();
       }

	public JSONObject GetUserTempTicketPriceAndKey(String email) {
                JSONObject jsonObj = new JSONObject();
		try {
			// HttpURLConnection
			String url = "https://jaspersui.pw/api/temp_ticket/get-user-temp-ticket-price-and-key/";
			URL endpoint = new URL(url);

                        String query = "{\"user_email\":\"" + email + "\"}";
			HttpURLConnection httpConnection = (HttpURLConnection) endpoint.openConnection();
			httpConnection.setRequestMethod("POST");
			httpConnection.setDoInput(true);
			httpConnection.setDoOutput(true);
			httpConnection.setRequestProperty("Content-Type", "application/json");
                        httpConnection.addRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.0)");
                        DataOutputStream outputStream = new DataOutputStream(httpConnection.getOutputStream());
                        outputStream.write(query.toString().getBytes("UTF-8"));
                        outputStream.flush();
                        outputStream.close();
			InputStreamReader isr = new InputStreamReader(httpConnection.getInputStream());
                        BufferedReader br = new BufferedReader(isr);
                        String line = "";
                        while( (line = br.readLine()) != null ) {
                            System.out.println(line);
                            jsonObj = new JSONObject(line);
                        }
                        
		}catch(Exception e){
                                e.printStackTrace();}
                return jsonObj;
	}
        
        public void Deposit(String email, int money) {
                JSONObject jsonObj = new JSONObject();
		try {
			// HttpURLConnection
			String url = "https://jaspersui.pw/api/user/deposit/";
			URL endpoint = new URL(url);

                        String query = "{\"user_email\": \""+email+"\",\"money\":"+ money + "}";
			HttpURLConnection httpConnection = (HttpURLConnection) endpoint.openConnection();
			httpConnection.setRequestMethod("POST");
			httpConnection.setDoInput(true);
			httpConnection.setDoOutput(true);
			httpConnection.setRequestProperty("Content-Type", "application/json");
                        httpConnection.addRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.0)");
                        DataOutputStream outputStream = new DataOutputStream(httpConnection.getOutputStream());
                        outputStream.write(query.toString().getBytes("UTF-8"));
                        outputStream.flush();
                        outputStream.close();
			InputStreamReader isr = new InputStreamReader(httpConnection.getInputStream());
                        BufferedReader br = new BufferedReader(isr);
                        String line = "";
                        while( (line = br.readLine()) != null ) {
                            System.out.println(line);
                        }
                        
		}catch(Exception e){
                                e.printStackTrace();}
	}
        
        public void Debit(String email, int money) {
                JSONObject jsonObj = new JSONObject();
		try {
			// HttpURLConnection
			String url = "https://jaspersui.pw/api/user/debit/";
			URL endpoint = new URL(url);

                        String query = "{\"user_email\": \""+email+"\",\"money\":"+ money + "}";
			HttpURLConnection httpConnection = (HttpURLConnection) endpoint.openConnection();
			httpConnection.setRequestMethod("POST");
			httpConnection.setDoInput(true);
			httpConnection.setDoOutput(true);
			httpConnection.setRequestProperty("Content-Type", "application/json");
                        httpConnection.addRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.0)");
                        DataOutputStream outputStream = new DataOutputStream(httpConnection.getOutputStream());
                        outputStream.write(query.toString().getBytes("UTF-8"));
                        outputStream.flush();
                        outputStream.close();
			InputStreamReader isr = new InputStreamReader(httpConnection.getInputStream());
                        BufferedReader br = new BufferedReader(isr);
                        String line = "";
                        while( (line = br.readLine()) != null ) {
                            System.out.println(line);
                        }
                        
		}catch(Exception e){
                                e.printStackTrace();}
	}

        
        	public Boolean TransferTicketDataToFormalTable(String email) {
                Boolean status = false;
                JSONObject jsonObj = new JSONObject();
		try {
			// HttpURLConnection
			String url = "https://jaspersui.pw/api/temp_ticket/transfer-ticket-data-to-formal-table/";
			URL endpoint = new URL(url);
                        String query = "{\"user_email\":\"" + email + "\"}";
			HttpURLConnection httpConnection = (HttpURLConnection) endpoint.openConnection();
			httpConnection.setRequestMethod("POST");
			httpConnection.setDoInput(true);
			httpConnection.setDoOutput(true);
			httpConnection.setRequestProperty("Content-Type", "application/json");
                        httpConnection.addRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.0)");
                        DataOutputStream outputStream = new DataOutputStream(httpConnection.getOutputStream());
                        outputStream.write(query.toString().getBytes("UTF-8"));
                        outputStream.flush();
                        outputStream.close();
			InputStreamReader isr = new InputStreamReader(httpConnection.getInputStream());
			BufferedReader br = new BufferedReader(isr);
                        String line = "";
                        while( (line = br.readLine()) != null ) {
                            System.out.println(line);
                            jsonObj = new JSONObject(line);
                        }
			br.close();
                        String statusString = jsonObj.getString("status").toString();
                        if (Objects.equals(statusString, "success")){
                            status = true;
                        }

		} catch(Exception e) {
			e.printStackTrace();
		}
		return status;
	}

	public Boolean getResultStatus() {
		return resultStatus;
	}

	public void setResultStatus(Boolean resultStatus) {
		this.resultStatus = resultStatus;
	}

	public String getResult() {
		return result;
	}

	public void setResult(String result) {
		this.result = result;
	}
}