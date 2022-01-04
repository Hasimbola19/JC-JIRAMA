package sid2;

import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.SystemException;
import javacard.framework.APDU;
import javacard.framework.JCSystem;
import javacard.framework.Util;
import javacard.framework.service.Dispatcher;
import javacard.framework.service.RMIService;
import javacard.framework.OwnerPIN;
import javacardx.apdu.ExtendedLength;
import sid2.JiramaImpl.*;


public class CompteurApplet extends Applet implements ExtendedLength{
	public static final short _0 = 0;
	
    // class APDU
    private static final byte CLA_APDU = (byte)0x80;

    
    // instruction APDU pour verifier pin
    private static final byte INS_VERIFY = (byte)0x20;
    private static final byte INS_GETCHALLENGE = (byte)0x84;
   
    
    // constate concernant PIN, 
  
    private static final byte MAX_PIN_SIZE = 8; // longueur maximal de pin
    private static final byte MIN_PIN_SIZE = 4; // longueur minimal
    private static final byte PIN_TRIES = 3; // maximum d'essaie d'entrer; en dehors c'est bloqué
    // puc et pin sont pareil; on utilise puc pour proteger pin
    private static final byte PUC_SIZE = 16;
    private static final byte PUC_TRIES = 3;

     // Etat de l'applet
    private static final byte STATE_INITIAL = 1;   // etat avant tout action
    private static final byte STATE_PREPERSONALISED = 2; // avant que le pin soit changé
    private static final byte STATE_PERSONALISED = 3;  // pin changé; prêt à etre utilise
  
    private static final short MAX_BLOCK_LEN = 256;
   
    // reponse SW en cas d'erreur de pin
 
    private static final short SW_PIN_INCORRECT_TRIES_LEFT = (short)0x63C0;

	private static final short TMP_SIZE = 18;

	private static final byte INS_CHANGEREFERENCEDATA = 0x40;

	private OwnerPIN puc = null;

    /*initialisation de la variable pin de la classe OwnerPin; null c'est un objet*/
    private OwnerPIN pin = null;
   
    private byte state = 0;

	private byte[] tmp = null;

    private Dispatcher dispatcher;
    
    /*Action dans la carte*/
    // Defining supported INS valuse for APDU Command.
    public static final byte INS_INIT_OPERAND = (byte) 0x10;
    public static final byte INS_ADD = (byte) 0x20;
    public static final byte INS_SUBTRACT = (byte) 0x30;

    // P1 parameter in the APDU command
    public static final byte OPERAND_A = 0x01;
    public static final byte OPERAND_B = 0x02;
    
 // Defining Exception Status Words
    public static short SW_NOT_EQUAL_LENGTH = (short) 0x6910;
    public static short SW_OVERFLOW_OCCURS = (short) 0x6911;
    public static short SW_UNDERFLOW_OCCURS = (short) 0x6912;
    
    public static byte[] OpA = new byte[8];
    public static byte[] OpB = new byte[8];
    public static byte[] result = new byte[8];

    public static byte lenA;
    public static byte lenB;

	
    // methode d'installation de notre applet, appeler à chaque utilisation de notre class CompteurApplet
    public static void install(byte[] bArray, short bOffset, byte bLength) throws SystemException {
        new CompteurApplet(bArray, bOffset, bLength);
    }
    
    // constructeur de notre classe CompteurApplet, lieu d'appelle de tout notre variable utilitaire
    protected CompteurApplet(byte[] bArray, short bOffset, byte bLength) {
    	
        //instaciation du variable pin,
        // avec initialisation d'essaie d'entré et de la logueur du pin
        // ce longueur dependra de l'initialisation de votre PIN
    	RMIService rmiService = new RMIService(new JiramaImpl());
    	dispatcher = new Dispatcher((short)1);
    	dispatcher.addService(rmiService, Dispatcher.PROCESS_COMMAND);
       
    	pin = new OwnerPIN(PIN_TRIES, MAX_PIN_SIZE);

        state = STATE_INITIAL;

        tmp = JCSystem.makeTransientByteArray(TMP_SIZE,  JCSystem.CLEAR_ON_DESELECT);
      
        // methode par defaut pour enregistrement; propre a javaCard
        register();
    }
    
    // deselect une fois deconnecter
    public void deselect() {
        pin.reset();
        puc.reset();
    }

    //methode d'activation de votre applet
    public boolean select(){   	
    	return true;
    }
    
    // methode de proceder pour communication à votre applet
    // la ou lieur tout les commande APDU 
    public void process(APDU apdu) throws ISOException{
            
        if(selectingApplet()){
        	return;
        }
        	//ISO7816.
        byte[] buf = apdu.getBuffer();
        byte cla = buf[ISO7816.OFFSET_CLA];
        byte ins = buf[ISO7816.OFFSET_INS];
        byte lc = buf[ISO7816.OFFSET_INS];
        byte p1 = buf[ISO7816.OFFSET_P1];
        
        // No secure messaging for the PKI applet
        if(cla == CLA_APDU){
        	switch (ins) {               	
        	case INS_VERIFY:
        		processVerify(apdu);
        		break;
        	case INS_CHANGEREFERENCEDATA:
        		processChangeReferenceData(apdu);
        		break;
        	case INS_INIT_OPERAND:
        		apdu.setIncomingAndReceive();
        		if(lc > (byte) 0x08) {
        			ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        		}if(p1 == OPERAND_A) {
        			Util.arrayCopyNonAtomic(buf, ISO7816.OFFSET_CDATA,  OpA, (short) 0x00, buf[ISO7816.OFFSET_LC]);
        		}
        	default:
            ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        	}
        }else{
    		ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
    	}
    }

   
    /**
     * instruction de verification (0x20)
     * voir ISO7816-4 Section 7.5.6
     *  
     */
    private void processVerify(APDU apdu) {
        if(state != STATE_PERSONALISED) {
            ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
        byte[] buf = apdu.getBuffer();
        if(buf[ISO7816.OFFSET_P1] != 0x00 || buf[ISO7816.OFFSET_P2] != 0x00) {
            ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
        }
        short lc = unsigned(buf[ISO7816.OFFSET_LC]);
        if(lc < MIN_PIN_SIZE || lc > MAX_PIN_SIZE) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        apdu.setIncomingAndReceive();
        // pas de gargbage collector; les donnes sont conserver avec le Le
        Util.arrayFillNonAtomic(buf, (short)(ISO7816.OFFSET_CDATA+lc),
                (short)(MAX_PIN_SIZE - lc), (byte)0x00);
        if(!pin.check(buf, ISO7816.OFFSET_CDATA, MAX_PIN_SIZE)) {
            ISOException.throwIt((short)(SW_PIN_INCORRECT_TRIES_LEFT | pin.getTriesRemaining()));
        }
    }

     private short unsigned(byte b) {
		// TODO Auto-generated method stub
		return (short) (b & 0x00FF) ;
	}

	/**
     * Process the CHANGE REFERENCE DATA instruction (0x24)
     * ISO7816-4 Section 7.5.7
     * 
     * Nous avons deux option: soit en etat de production et modifier le puc,
     * soit en distribution et en operation et changer le pin 
     */
    private void processChangeReferenceData(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        short lc = unsigned(buf[ISO7816.OFFSET_LC]);
        byte p1 = buf[ISO7816.OFFSET_P1];
        byte p2 = buf[ISO7816.OFFSET_P2];

        if(state > STATE_INITIAL) {

        // Changement du pin; puc doit etre procuré 
        // ici, j'ai utilisé P1 = 0x00: et verification avec le donnée de (puc) suivi du dennée de réfernce (pin) 

        if(p1 != 0x00 || p2 != (byte)0x00) {
            ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
        }
        short pinSize = (short)(lc - PUC_SIZE);
        if(pinSize < MIN_PIN_SIZE || pinSize > MAX_PIN_SIZE) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);            
        }
        apdu.setIncomingAndReceive();
        short offset = (short)(ISO7816.OFFSET_CDATA+PUC_SIZE);
        for(short i=0;i<pinSize;i++) {
            byte b = buf[(short)(offset+i)];
            if(b < (byte)0x30 || b > (byte)0x39) {
                ISOException.throwIt(ISO7816.SW_WRONG_DATA);
            }            
        }
       
        Util.arrayFillNonAtomic(buf, (short)(offset+pinSize), (short)(MAX_PIN_SIZE - pinSize), (byte)0x00);
        if(!puc.check(buf, ISO7816.OFFSET_CDATA, PUC_SIZE)) {
            ISOException.throwIt((short)(SW_PIN_INCORRECT_TRIES_LEFT | puc.getTriesRemaining()));
        }
        pin.update(buf, offset, MAX_PIN_SIZE);
        pin.resetAndUnblock();
        if(state == STATE_PREPERSONALISED) {
            state = STATE_PERSONALISED;
        }
        }else{
            // en etat de production; changer le puc
            if(p1 != 0x01 || p2 != 0x00) {
                ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
            }
            if(lc != PUC_SIZE) {
                ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
            }
            apdu.setIncomingAndReceive();
            puc.update(buf, ISO7816.OFFSET_CDATA, (byte)lc);        
            puc.resetAndUnblock();
        }
    }

}
