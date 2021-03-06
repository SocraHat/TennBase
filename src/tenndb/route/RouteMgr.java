package tenndb.route;

import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import tenndb.base.Cell;
import tenndb.common.FileMgr;
import tenndb.data.ByteBufferMgr;
import tenndb.data.Colunm;
import tenndb.data.DBPage;
import tenndb.dist.DistMgr;
import tenndb.log.LogMgr;
import tenndb.thread.ImportThread;
import tenndb.tx.TransMgr;


public class RouteMgr {

	protected Cell level0 = null;
	
    protected Map<String,  Cell>  level1  = null;
    protected Map<String,  Cell>  level2  = null;
    
    protected final String        root;
	protected final String        cataName;	
    protected final FileMgr       rootMgr;
    protected final LogMgr        logMgr;
	protected final TransMgr      transMgr;
    protected final ByteBufferMgr bufMgr;
    
	protected ImportThread importThread = null;
	
	protected final ReadWriteLock lock = new ReentrantReadWriteLock(false);
	
    protected volatile boolean initialized = false;
    
	public static final String LOGS_PATH  = System.getProperty("file.separator") + "logs_data";
	
	public static final String ROUTE_PATH = System.getProperty("file.separator") + "route_data";

	public static final String DATA_PATH  = System.getProperty("file.separator") + "raw_data";

	
	public RouteMgr(String root){
		this.cataName       = "date_route";
		this.root           = root;
		this.rootMgr        = new FileMgr(this.root);
		
		this.logMgr   		= new LogMgr(this.cataName, this.root + LOGS_PATH);
		this.bufMgr   		= new ByteBufferMgr(DBPage.PAGE_SIZE);
		this.transMgr 		= new TransMgr();
		
		this.level0         = new Cell(this.cataName, 0, new FileMgr(this.root + ROUTE_PATH), this.transMgr, this.logMgr);
		this.level1         = new Hashtable<String, Cell>();
		this.level2         = new Hashtable<String, Cell>();
	}
	
	public void init(){
		if(false == this.initialized){
			try{
				this.lockWrite();
				if(false == this.initialized){
					this.level0.init();
					this.importThread = new ImportThread(new FileMgr(this.root + DistMgr.DIST_PATH), this);
					this.importThread.start();
					this.initialized = true;
				}
			}catch(Exception e){
				System.out.println(e);
			}finally{
				this.unLockWrite();
			}
		}
	}
	
	public final Cell getLevel0(){
		return this.level0;
	}
	
	public static final String data2path(String date){
		String path = null;
		//20160701
		if(null != date && date.length() == 8){
			path = System.getProperty("file.separator") + date.substring(0, 4) 
			     + System.getProperty("file.separator") + date.substring(4, 6) 
			     + System.getProperty("file.separator") + date.substring(6, 8) ;
		}
		
		return path;
	}
	
	public static final String dev2path(String dev){
		String path = null;
		
		if(null != dev && dev.length() == 10){
			path = System.getProperty("file.separator") + dev.substring(8, 10) 
			     + System.getProperty("file.separator") + dev.substring(0,  8);
		}
		
		return path;
	}
	
	public final Cell pinLevel2(String level1, String level2){
		Cell cell2 = null;
		
		try{
			if(null != level1 && level1.length() > 0 && null != level2 && level2.length() > 0){
				String key = level1 + "_" + level2;

//				this.lockRead();
				cell2 = this.level2.get(key);
//				this.unLockRead();
				
				if(null == cell2){
					Cell cell1 = this.pinLevel1(level1);
					if(null != cell1){

						Colunm colunm = cell1.search(Colunm.hashCode(level2));
						if(null == colunm){
							colunm = new Colunm(level2, 1);						
							cell1.insert(colunm.getHashCode(), colunm);
						}
						
						if(null != colunm){

							String key2 = colunm.getKey();
							if(null != key2){
								String datepath = data2path(level1);
								String devpath  = dev2path (level2);
								//dev2path
								cell2 = new Cell(key2, 0, new FileMgr(this.root + DATA_PATH + datepath + devpath), this.transMgr, this.logMgr);	
								cell2.init();

								try{
//									this.unLockRead();
									this.lockWrite();
									this.level2.put(key, cell2);	
								}catch(Exception e){
									System.out.println(e);
								}
								finally{
									this.unLockWrite();
//									this.lockRead();
								}
							}
						}
					}
				}
			}									
		}catch(Exception e){
			System.out.println(e);
		}finally{
//			this.unLockRead();
		}
		
		return cell2;
	}
	
	//160101
	public final Cell pinLevel1(String level1){
		
		Cell cell = null;
		
		try{
			if(null != level1 && level1.length() > 0){
//				this.lockRead();
				
				cell = this.level1.get(level1);
				if(null == cell){
					Colunm colunm = this.level0.search(Colunm.hashCode(level1));
					if(null == colunm){
						colunm = new Colunm(level1, 1);						
						this.level0.insert(colunm.getHashCode(), colunm);
					}
					
					if(null != colunm){
						String key = colunm.getKey();
						if(null != key){
							String path = data2path(key);
							cell = new Cell(key, 0, new FileMgr(this.root + DATA_PATH + path), this.transMgr, this.logMgr);	
							cell.init();
							try{
//								this.unLockRead();
								this.lockWrite();
								this.level1.put(key, cell);	
							}catch(Exception e){
								System.out.println(e);
							}
							finally{
								this.unLockWrite();
//								this.lockRead();
							}
						}			
					}
				}
			}			
		}catch(Exception e){
			System.out.println(e);
		}finally{
//			this.unLockRead();
		}
		return cell;
	}
	
	protected void lockRead()    { this.lock.readLock().lock();    }
	
	protected void unLockRead()  { this.lock.readLock().unlock();  }

	protected void lockWrite()   { this.lock.writeLock().lock();   }
	
	protected void unLockWrite() { this.lock.writeLock().unlock(); }
	
}
