/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.DriverManager;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Observer;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * This is the heart of Ricart and Agrawala algorithm
 */
public class CMutualExclusionManager implements Observer{
    //There are three states for a process
    // 1) NONCRITICAL: It has already exited the CS and do not wishes to enter again for a while
    // 2) TRYING: It is requesting to enter the CS.
    // 3) CRITICAL: It is in the Critical Section
    public enum ProcessState{
        PS_NONCRITICAL,PS_TRYING,PS_CRITICAL
    };
    // Token (Reply message in optimisation) at evey single moment belongs to me, to the neighbor or neigther of us.
    public enum TokenPossessionStatus{
        TOKEN_MINE,TOKEN_MIDDLE,TOKEN_NEIGHBOR
    };
    // Constants of the algorithm(Feel free to change them entirely)
    private static int                      UNIT_OF_TIME = 10;   
    public static final int                 MAX_CRITICAL_SECTION_TIME = 3;
    public static final int                 MAX_PROCESS_NUMBER = 5;
    //Refer to project definition
    public static final int                 MAX_REQUIRED_CS_USE = 5;
    //Process number between [0-9]
    private int                             m_nProcessId;
    private ProcessState                    m_eProcessState;
    private CLamportClock                   m_LogicalClock;
    // Status of tokens I have as well as thos I don't have currently
    private TokenPossessionStatus[]         m_arrTokenPossessionStatus;
    // Pending replies according to the algorithm. 
    private ArrayList<Integer>              m_arrPendingReply;
    // Network layer instance
    private CNetworkLayer                   m_Network;
    // Time Stamp of my newest request
    private CTimeStamp                      m_tsMyRequest;
    private boolean                         m_bTesterEnabled;
    // Tell me how many times have you entered critical section?
    private int                             m_nCSEntryCount;
    //set once I receive kill signal from node 0
    private boolean                         m_bExit;
    // set after 40 times of using CS
    private boolean                         m_bMissionComplete;
    // Node 0 sets to know howmany processes have been completed 
    private boolean[]                       m_bProcessCompleteMessageReceived;
    
    // Data Collection fields       
    private int                             m_nTotalSent;
    private int                             m_nTotalRcvd;
    
    private int[]                           m_nAttemptSent;
    private int[]                           m_nAttemptRcvd;
    private long[]                          m_nLatency;
    
    private int[]                           m_nREQSentPerAttempt;
    private int[]                           m_nREPSentPerAttempt;
    private int[]                           m_nREQRcvdPerAttemptWithLowerTimeStamp;
    private int[]                           m_nREPRcvdPerAttempt;
    
    CStopwatch                              m_stopwatch;
    // Logfile physical address
    public String                           m_strLogURL;
    File                                    m_File;
    BufferedWriter                          m_log;
    Semaphore                               m_mutex;
    // All nodes other than 0 have to receive a req from node 0 in order to start the algorithm
    boolean                                 m_bAnyRequestReceivedFromNode0;
    //[--XX-X-X---] this string is logged in the file to have a better picture of an specific situation in log file
    private String[]                        m_strTokenOwnershipStatus;
    
    
    
    public CMutualExclusionManager(int x_nProcessId,CNetworkLayer x_network, int x_nTimeUnit)
    {
        try 
        {
            m_nProcessId = x_nProcessId;
            UNIT_OF_TIME = x_nTimeUnit;
            m_eProcessState = ProcessState.PS_NONCRITICAL;
            m_LogicalClock = new CLamportClock(m_nProcessId);
            m_arrTokenPossessionStatus = new TokenPossessionStatus [MAX_PROCESS_NUMBER];
            m_arrPendingReply = new ArrayList<Integer>();
            m_Network = x_network;
            m_Network.addObserver(this);
            m_tsMyRequest = new CTimeStamp(0, x_nProcessId);
            m_nCSEntryCount = 0;
            m_bExit = false;
            m_bMissionComplete = false;
            m_bProcessCompleteMessageReceived = new boolean[MAX_PROCESS_NUMBER];
            
            m_nTotalRcvd = 0;
            m_nTotalSent = 0;
            
            m_nAttemptSent = new int[MAX_REQUIRED_CS_USE];
            m_nAttemptRcvd = new int[MAX_REQUIRED_CS_USE];
            m_nLatency = new long[MAX_REQUIRED_CS_USE];
            m_strTokenOwnershipStatus = new String[MAX_REQUIRED_CS_USE];
            
            m_nREQSentPerAttempt = new int[MAX_REQUIRED_CS_USE];
            m_nREPSentPerAttempt = new int[MAX_REQUIRED_CS_USE];
            m_nREQRcvdPerAttemptWithLowerTimeStamp = new int[MAX_REQUIRED_CS_USE];
            m_nREPRcvdPerAttempt = new int[MAX_REQUIRED_CS_USE];
            
            m_stopwatch = new CStopwatch();
            m_strLogURL = "log" + Integer.toString(x_nProcessId) + ".txt";
            m_File = new File(m_strLogURL);
            m_log = new BufferedWriter(new FileWriter(m_File));
            m_mutex = new Semaphore(1);
            
            if(m_nProcessId == 0)
                m_bAnyRequestReceivedFromNode0 = true;
            else
                m_bAnyRequestReceivedFromNode0 = false;
            
            
            for(int i = 0 ; i < MAX_REQUIRED_CS_USE ; i++)
            {
                m_nAttemptRcvd[i] = 0;
                m_nAttemptSent[i] = 0;
                m_nLatency[i] = 0;
                m_strTokenOwnershipStatus[i]="";
                
                m_nREQSentPerAttempt[i] = 0;
                m_nREPSentPerAttempt[i] = 0;
                m_nREPRcvdPerAttempt[i] = 0;
                m_nREQRcvdPerAttemptWithLowerTimeStamp[i] = 0;                
            }          
            
            for(int i = 0 ; i < MAX_PROCESS_NUMBER ; i++)
            {
                m_arrTokenPossessionStatus[i] = TokenPossessionStatus.TOKEN_MIDDLE;
                m_bProcessCompleteMessageReceived[i] = false;
            }
        } 
        catch (IOException ex) 
        {
            
        }        
    }
    // It is called by the children upon receiving messages according to Observer design pattern
    public void update(Observable obj,Object arg)
    {
        if(arg instanceof CInterProcessMessage)
        {
            OnReceiveMessage((CInterProcessMessage)arg);
        }
        else
        {
            System.out.println("2 ) Invalid Serialized Message Received...");
            System.exit(0);
        }
    }
    // Every process which is allowed to enter CS, calls this method
    public void CriticalSection()
    {
        try 
        {
            OnEnterCriticalSection();
            // Its a short time, but it could implemented better using a while, stopwatch and less blocking fashion
            Thread.sleep(MAX_CRITICAL_SECTION_TIME * UNIT_OF_TIME);
            OnExitCriticalSection();
        } 
        catch (InterruptedException ex) 
        {
            
        }
    }
    public int GetTimeUnit()
    {
        return UNIT_OF_TIME;
    }
    //Set the value received from program command line parameter
    public void SetTimeUnit(int x_nTimeUnit)
    {
        Log("Time Unit Set to "+x_nTimeUnit+" (ms)");
        UNIT_OF_TIME = x_nTimeUnit;
    }
    public boolean ShallIExit()
    {
        return m_bExit;
    }
    // calculates how much wait is needed between two consecutive try.
    // According to the project definition two phases
    public long  CalculateTheTimeBetweenTwoConsecutiveTry()
    {
        double nWaitCount = 0;
        int nMiliSeconds = 0;
        if(m_nCSEntryCount < 20 || (m_nProcessId % 2 != 0)) // Phase 1 for All and Phase 2 for Odds
        {
            nWaitCount = (10 - 5) / MAX_PROCESS_NUMBER;
            nWaitCount = 5 + (nWaitCount * m_nProcessId);
        }
        else // Phase 2 for even ProcessIds
        {
            nWaitCount = (50 - 45) / MAX_PROCESS_NUMBER;
            nWaitCount = 45 + (nWaitCount * m_nProcessId);            
        }
        nMiliSeconds = (int)Math.ceil(nWaitCount) * UNIT_OF_TIME;
        
        return nMiliSeconds;        
    }
    // Tell me how many times have you entered critical section?
    public int GetCSEntryCount()
    {
        return m_nCSEntryCount;
    }
    // Is Called by the children threads once a message has been received
    public void OnReceiveMessage(CInterProcessMessage x_msg)
    {
        //Updating Logical Clock
        m_LogicalClock.OnInternalEvent();
        m_LogicalClock.OnReceiveNeighborProcessTimestamp(x_msg.GetTimeStamp());
        
        //x_msg.log(CInterProcessMessage.EMessageDirection.MD_RECEIVE);
        if(x_msg.GetDestinationProcessId() != m_nProcessId)
        {
            System.out.println("OnReceiveMessage(): Unexpected Message Received for ProcessId " + x_msg.GetDestinationProcessId() +" while mine is " + m_nProcessId);
            System.exit(-1);
        }
        if(x_msg.GetSourceProcessId() < 0 || x_msg.GetSourceProcessId() >= MAX_PROCESS_NUMBER)
        {
            System.out.println("OnReceiveMessage(): Message Received From an Invalid ProcessId " + x_msg.GetSourceProcessId());
            System.exit(-1);           
        }
        if(x_msg.GetMessageType() == CInterProcessMessage.EMessageType.MT_CS_MISSION_COMPLETE)
        {
            m_bProcessCompleteMessageReceived[x_msg.GetSourceProcessId()] = true;
        }
        else if(x_msg.GetMessageType() == CInterProcessMessage.EMessageType.MT_KILL)
        {
            m_bExit = true;
        }
        else if(x_msg.GetMessageType() == CInterProcessMessage.EMessageType.MT_REPLY)
        {
            m_nTotalRcvd++;
            m_arrTokenPossessionStatus[x_msg.GetSourceProcessId()] = TokenPossessionStatus.TOKEN_MINE;
            if(m_eProcessState == ProcessState.PS_TRYING)
            {
                m_nAttemptRcvd[m_nCSEntryCount]++;
                m_nREPRcvdPerAttempt[m_nCSEntryCount]++;
            }
        }
        else if(x_msg.GetMessageType() == CInterProcessMessage.EMessageType.MT_REQUEST)
        {
            //--> A signal to indicate the proper time to start
            if(x_msg.GetSourceProcessId() == 0)
                m_bAnyRequestReceivedFromNode0 = true;
            
            if(m_eProcessState == ProcessState.PS_TRYING && m_tsMyRequest.IsGreater(x_msg.GetTimeStamp()))
            {
                m_nREQRcvdPerAttemptWithLowerTimeStamp[m_nCSEntryCount]++;
            }
            
            
            m_nTotalRcvd++;
            if(should_I_Reply_This_Request(x_msg) == true)
            {
                TokenPossessionStatus eLast_Token_status =  m_arrTokenPossessionStatus[x_msg.GetSourceProcessId()];
                m_arrTokenPossessionStatus[x_msg.GetSourceProcessId()] = TokenPossessionStatus.TOKEN_NEIGHBOR;
                
                send_reply(x_msg.GetSourceProcessId());
                //If I yield the token to a higher priority and I'm requesting, now that I have granted the token, I have to send my request
                // afterwards to let the receiving process know that I need the token
                if(m_eProcessState == ProcessState.PS_TRYING && eLast_Token_status == TokenPossessionStatus.TOKEN_MINE)
                {
                    send_request(x_msg.GetSourceProcessId());
                }
            }
            else
            {
                try // Put this request in your Pocket to answer it in the future once you have done
                {
                    m_mutex.acquire();
                    if(!m_arrPendingReply.contains(x_msg.GetSourceProcessId()))
                    {
                        m_arrPendingReply.add(x_msg.GetSourceProcessId());
                    }
                    m_mutex.release();
                } 
                catch (InterruptedException ex) 
                {
                
                }
            }
        }
        else if(x_msg.GetMessageType() != CInterProcessMessage.EMessageType.MT_NOTHING)
        {
            System.out.println("OnReceiveMessage(): Invalid Message Type Received In ProcessId  " + m_nProcessId);
            System.exit(-1);           
        }
    }
    //It is called upon entering CS
    private void OnEnterCriticalSection()
    {
        if(m_eProcessState != ProcessState.PS_TRYING)
        {
            System.out.println("OnEnterCriticalSection(): Error in Application layer, Unexpected State " + m_eProcessState);
            System.exit(-1);           
        }
        Log("[P"+m_nProcessId+"]"+"[" +m_nCSEntryCount +"]" +" entering on "+GetCurrentTime());
        m_nLatency[m_nCSEntryCount] = m_stopwatch.elapsedTime();
        m_nCSEntryCount++;
        send_diagnosis(CInterProcessMessage.EMessageType.MT_CS_ENTRY);
        m_eProcessState = ProcessState.PS_CRITICAL;
        //System.out.println("["+m_nCSEntryCount +"] Entering On "+ GetCurrentTime());
    }
    //It is called upon exit of CS
    private void OnExitCriticalSection()
    {
        m_LogicalClock.OnInternalEvent();
        send_diagnosis(CInterProcessMessage.EMessageType.MT_CS_EXIT);
        if(m_eProcessState != ProcessState.PS_CRITICAL)
        {
            System.out.println("OnEnterCriticalSection(): Error in Application layer, Unexpected State " + m_eProcessState);
            System.exit(-1);           
        }
        m_eProcessState = ProcessState.PS_NONCRITICAL;
        //System.out.println("Non Critical Section...");
        
        //Is Called according to the algorithm, once you come out of Critical Section
        handleDifferedReplies();
        
        //If you have used CS 40 times, your mission is complete
        //send mission completion to node 0 if you are not node 0
        if(m_nCSEntryCount == MAX_REQUIRED_CS_USE)
        {
            m_bMissionComplete = true;
            if(m_nProcessId != 0 )
            {
                Log("Sending Mission Completion to Node 0");
                send_mission_completion();
            }
            else // For Process 0
            {
                Log("Mission Complete...");
                m_bProcessCompleteMessageReceived[0] = true;
            }
        }
    }
    public boolean AllProcessFinished()
    {
        
        boolean bRet = true;
        for(int i = 0 ; i < MAX_PROCESS_NUMBER ; i++)
        {  
            if(m_bProcessCompleteMessageReceived[i] != true)
            {
                bRet = false;
                break;
            }
        }
        return bRet;
    }
    public void KillAll()
    {
        send_kill_message_to_all();
    }
    public boolean IsMissionComplete()
    {
        return m_bMissionComplete;
    }
    public ProcessState GetProcessState()
    {
        return m_eProcessState;
    }
    //A process calls this method once it wishes to enter CS
    public void Request()
    {
        if(!m_bAnyRequestReceivedFromNode0)
        {
            //System.out.println("Kepp waiting for Node 0...");
            return;
        }
        if(m_eProcessState != ProcessState.PS_NONCRITICAL) // 
        {
            System.out.println("Request(): Error in Application layer, Unexpected State " + m_eProcessState);
            System.exit(-1);           
        }
        
        
        m_LogicalClock.OnInternalEvent();
        m_tsMyRequest.SetTimeStamp(m_LogicalClock.GetTimeStamp().GetTime());
        //System.out.println("Trying at TIME_STAMP("+m_tsMyRequest.GetTime()+","+ m_tsMyRequest.GetProcessId()+")");
        m_eProcessState = ProcessState.PS_TRYING;
        m_stopwatch.Start();
        
        for(int i = 0 ; i < MAX_PROCESS_NUMBER ; i++)
        {
            if(i == m_nProcessId) // You should'nt send request to yourself!
            {
                m_strTokenOwnershipStatus[m_nCSEntryCount] += "X";
                continue;
            }
            //--> Optimisation
            if(m_arrTokenPossessionStatus[i] == TokenPossessionStatus.TOKEN_MIDDLE 
                || m_arrTokenPossessionStatus[i] == TokenPossessionStatus.TOKEN_NEIGHBOR)
            {
                m_strTokenOwnershipStatus[m_nCSEntryCount] += "-";
                send_request(i);
            }
            else
            {
                m_strTokenOwnershipStatus[m_nCSEntryCount] += "X";
            }
            //<-- Optimisation
        }
    }
    // A process can enter CS only if this method returns true
    // The conditions are:
    //1) You have all the tokens
    //2) You already have a request
    public boolean AmIAllowedToEnterCS()
    {
        boolean bRet = false;
        if(m_eProcessState == ProcessState.PS_TRYING && count_my_tokens() == (MAX_PROCESS_NUMBER - 1))
        {
            bRet = true;
        }
        return bRet;
    }
    //Counts howmany tokens I currently have
    public int count_my_tokens()
    {
        int nTokenCount = 0;
        for(int i = 0 ; i < MAX_PROCESS_NUMBER ; i++)
        {
            //You definitely have your own token
            if(i == m_nProcessId)
                continue;
            if(m_arrTokenPossessionStatus[i] == TokenPossessionStatus.TOKEN_MINE)
            {
                nTokenCount++;
            }
        }
        return nTokenCount;        
    }
    //Is Called according to the algorithm, once you come out of Critical Section
    private void handleDifferedReplies()
    {
        try 
        {
            m_mutex.acquire();
            Iterator<Integer> itr = m_arrPendingReply.iterator();
            while (itr.hasNext())
            {
                int nDestProcessId = itr.next();
                m_arrTokenPossessionStatus[nDestProcessId] = TokenPossessionStatus.TOKEN_NEIGHBOR;
                send_reply(nDestProcessId);
                
            }
            m_arrPendingReply.clear();
            m_mutex.release();
        }
        catch (InterruptedException ex) 
        {
            
        }
    }
    //If I'm neighther requesting nor using the CS, I reply it.
    // If I'm requesting and have a higher time stamp I also yield the token
    private boolean should_I_Reply_This_Request(CInterProcessMessage x_msg)
    {
        boolean bRet = false;
        if(m_eProcessState == ProcessState.PS_NONCRITICAL)
        {
            bRet = true;
        }
        if(m_eProcessState == ProcessState.PS_TRYING )
        {
            if( m_tsMyRequest.IsGreater(x_msg.GetTimeStamp()) == true)
            {
                bRet = true;
            }
        }
        return bRet;        
    }
    // Sends MT_REQ to another process
    private void send_request(int x_nDestPId)
    {
        if(x_nDestPId < 0 || x_nDestPId >= MAX_PROCESS_NUMBER)
        {
            System.out.println("create_request(): Invalid Destination Process Id " + x_nDestPId);
            System.exit(-1);
        }
        m_nTotalSent++;
        
        if(m_eProcessState == ProcessState.PS_TRYING)
        {
            m_nAttemptSent[m_nCSEntryCount]++;
            m_nREQSentPerAttempt[m_nCSEntryCount]++;
        }
        
        //CTimeStamp time_Stamp = m_LogicalClock.GetTimeStamp();
        //CInterProcessMessage msg = new CInterProcessMessage(m_nProcessId,x_nDestPId, time_Stamp, CInterProcessMessage.EMessageType.MT_REQUEST);
        int nTimeStamp = m_tsMyRequest.GetTime();
        CInterProcessMessage msg = new CInterProcessMessage(m_nProcessId,x_nDestPId, new CTimeStamp(nTimeStamp, m_nProcessId), CInterProcessMessage.EMessageType.MT_REQUEST);
       // msg.log(CInterProcessMessage.EMessageDirection.MD_SEND);
        m_Network.SendMessage(msg);
    }
    // send Reply message according to the algorithm
    private void send_reply(int x_nDestPId)
    {
        if(x_nDestPId < 0 || x_nDestPId >= MAX_PROCESS_NUMBER)
        {
            System.out.println("create_reply(): Invalid Destination Process Id " + x_nDestPId);
            System.exit(-1);
        }
        m_nTotalSent++;
        
        if(m_eProcessState == ProcessState.PS_TRYING)
        {
            m_nAttemptSent[m_nCSEntryCount]++;
            m_nREPSentPerAttempt[m_nCSEntryCount]++;
        }
        
        CTimeStamp time_stamp = m_LogicalClock.GetTimeStamp();
        CInterProcessMessage msg = new CInterProcessMessage(m_nProcessId,x_nDestPId,time_stamp, CInterProcessMessage.EMessageType.MT_REPLY);
        //msg.log(CInterProcessMessage.EMessageDirection.MD_SEND);
        m_Network.SendMessage(msg);
        
    }  
    // Sending messages to an external tester.(not implemented completely
    private void send_diagnosis(CInterProcessMessage.EMessageType x_eMsgType)
    {
        if(!m_bTesterEnabled)
            return;
        CInterProcessMessage msg = new CInterProcessMessage(m_nProcessId, 99, m_LogicalClock.GetTimeStamp(), x_eMsgType);
        m_Network.SendMessage(msg);
    }
    // Once a process used CS for 40 times, it sends this message to node 0
    private void send_mission_completion()
    {
        CTimeStamp time_stamp = m_LogicalClock.GetTimeStamp();
        CInterProcessMessage msg = new CInterProcessMessage(m_nProcessId,0,time_stamp, CInterProcessMessage.EMessageType.MT_CS_MISSION_COMPLETE);
        m_Network.SendMessage(msg);
    }
    // Node 0 sends this message to all other nodes to bring the computation session to end
    private void send_kill_message_to_all()
    {
        m_bExit = true;
        if(m_nProcessId != 0) // Only Process 0 is in charge of ending up the calculation
            return;
        Log("Sending Kill Message to all Processes...");
        for(int i = 1 ; i < MAX_PROCESS_NUMBER ; i++)
        {
            CTimeStamp time_stamp = m_LogicalClock.GetTimeStamp();
            CInterProcessMessage msg = new CInterProcessMessage(0,i,time_stamp, CInterProcessMessage.EMessageType.MT_KILL);
            m_Network.SendMessage(msg);    
        }
    }
    // Logging essential data and analysis in the log file
    public void LogData()
    {
        try {
            Log("------------------------------------------------");
            for(int i = 0 ; i < MAX_REQUIRED_CS_USE ; i++)
            {
                Log("["+i+"]:TOKENS["+m_strTokenOwnershipStatus[i] +"] Tx("+m_nAttemptSent[i]+") Including REQ: "+m_nREQSentPerAttempt[i]+" REP: "+m_nREPSentPerAttempt[i]+" Rx("+m_nAttemptRcvd[i]+") Including REQ: "+m_nREQRcvdPerAttemptWithLowerTimeStamp[i]+" REP: "+m_nREPRcvdPerAttempt[i] +" Latancy(ms) = "+m_nLatency[i]);
            }
            Log("------------------------------------------------");
            Log("Total Sent: "+m_nTotalSent);
            Log("Total Rcvd: "+m_nTotalRcvd);
            Log("------------------------------------------------");           
            
            m_log.flush();
            m_log.close();
        } catch (IOException ex) {
           
        }
    }
    // Get current physical time as accurate as miliseconds
    public String GetCurrentTime()
    {
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        Date now = new Date();
        String strDate = sdfDate.format(now);
        return strDate;
    }
    private void Log(String x_strLog)
    {
        try 
        {
            m_log.write(x_strLog+"\r\n");
            System.out.println(x_strLog);
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
            System.exit(0);     
        }
    }
}
