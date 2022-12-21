/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */


import java.io.Serializable;

/* 
Time Stamp Structure. As part of the message sent over tcp links, it must implement serializable interface
It is composed of two fields. 1) Logical time and 2) Process ID
*/
public class CTimeStamp implements Serializable {
    private int m_nTime = -1;
    private int m_nPId  = -1;

    public CTimeStamp() {
    
    }
    public CTimeStamp(int x_nTime,int x_nPId)
    {
        m_nTime = x_nTime;
        m_nPId = x_nPId;
    }
    public int GetTime()
    {
        return m_nTime;
    }
    public int GetProcessId()
    {
        return m_nPId;
    }
    //Compares two timestamps based on:
    //1) their value
    //2) if the value is equal, then we compare the process Ids.
    public boolean IsGreater(CTimeStamp x_ts)
    {
        boolean bRes = false;
        if( m_nTime > x_ts.GetTime())
        {
            bRes = true;
        }
        else if(m_nTime == x_ts.GetTime())
        {
            if(m_nPId > x_ts.GetProcessId())
            {
                bRes = true;
            }
        }
        return bRes;
    }  
    public void SetTimeStamp(int x_nTime)
    {
        m_nTime = x_nTime;
    }
    public void SetProcess(int x_nPId)
    {
        m_nPId = x_nPId;
    }
}
