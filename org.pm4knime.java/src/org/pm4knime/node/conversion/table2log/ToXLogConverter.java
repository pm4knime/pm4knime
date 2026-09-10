package org.pm4knime.node.conversion.table2log;

import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.deckfour.xes.extension.XExtension;
import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.extension.std.XLifecycleExtension;
import org.deckfour.xes.extension.std.XOrganizationalExtension;
import org.deckfour.xes.extension.std.XTimeExtension;
import org.deckfour.xes.factory.XFactory;
import org.deckfour.xes.factory.XFactoryRegistry;
import org.deckfour.xes.info.impl.XLogInfoImpl;
import org.deckfour.xes.model.XAttributable;
import org.deckfour.xes.model.XAttribute;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.def.BooleanCell;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.data.def.IntCell;
import org.knime.core.data.def.StringCell;
import org.knime.core.data.time.localdatetime.LocalDateTimeCell;
import org.knime.core.data.time.localdatetime.LocalDateTimeCellFactory;
import org.knime.core.data.time.zoneddatetime.ZonedDateTimeCell;
import org.knime.core.data.time.zoneddatetime.ZonedDateTimeCellFactory;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.NodeLogger;
import org.pm4knime.util.XLogSpecUtil;
import org.pm4knime.util.XLogUtil;
import org.processmining.log.csvimport.config.CSVConversionConfig.CSVErrorHandlingMode;
import org.processmining.log.utils.XUtils;

/**
 * this class belongs to utility. But currently use is to create an EventLog from CSV file.
 * @author kefang-pads
 * @reference org.processmining.log.csvimport.handler.XESConversionHandlerImpl
 * @modify reason: add trace and event attribute sets. So we can assign them in category
 * @modify 16 Dec 2019. To change the model to add lifecycle transition for building event log.  
 */
public class ToXLogConverter {
	
	protected Table2XLogConverterNodeSettings m_settings;

	private XFactory factory;
	
	private XLog log = null;
	private XTrace currentTrace = null;
	private List<XEvent> currentEvents = new ArrayList<>();
	private boolean errorDetected = false;
	
	private XEvent currentEvent = null;
	private XEvent currentStartEvent;
	
	NodeLogger logger;
	
	private Map<String, DataCell> traceAttrMap = new HashMap<String, DataCell>();
	
	
	public void convertDataTable2Log(BufferedDataTable csvData, Table2XLogConverterNodeSettings m_settings2, ExecutionContext exec) throws CanceledExecutionException {
		
		this.m_settings = m_settings2;
		this.factory = XFactoryRegistry.instance().currentDefault();
		DataTableSpec spec = csvData.getDataTableSpec();
		String[] all_columns = spec.getColumnNames();
		
		String[] traceColumns = m_settings.m_columnFilterTrace.filterFromFullSpec(spec);
		List<String> traceList = Arrays.asList(traceColumns);
		Set<String> traceSet = new HashSet<>(traceList);
		
		List<String> eventList = new ArrayList<>();
		for (String col : all_columns) {
			exec.checkCanceled();
		    if (!traceSet.contains(col)) {
		        eventList.add(col);
		    }
		}
		String[] eventColumns = eventList.toArray(new String[0]);
		
		int[] traceColIndices = csvData.getDataTableSpec().columnsToIndices(traceColumns);
		int[] eventColIndices = csvData.getDataTableSpec().columnsToIndices(eventColumns);
		boolean[] traceColVisited = new boolean[traceColIndices.length];
		boolean[] eventColVisited = new boolean[eventColIndices.length];
		
		int caseIDIdx = -1, eventClassIdx, tsIdx = -1;
		
//		caseIDIdx = traceColumns.indexOf(config.getMCaseID().getStringValue());
//		eventClassIdx = eventColumns.indexOf(config.getMEventClass().getStringValue());
//		
		caseIDIdx = traceList.indexOf(m_settings.case_id);
		eventClassIdx = eventList.indexOf(m_settings.event_class);

//		String tsFormat = config.getMTSFormat().getStringValue();
//		df = DateTimeFormatter.ofPattern(tsFormat);
		
		// optional for lifecycle column, but there is no need to specify the event ID for it!!  Lifecycle is useful!!
		boolean withLifecycle = false; 
		int  lifecycleIdx = -1;
//		if(!config.getMLifecycle().getStringValue().equals(SMTable2XLogConfig.CFG_NO_OPTION)) {
		if(m_settings.use_life_cycle
				&& !Table2XLogConverterNodeSettings.isNoColumnSelected(m_settings.life_cycle)) {
			// exception happens, when eventAttrSet excluses life-cycle column, which one is the optimal choices?
			// if we choose lifecycle there, then we should keep it into our event attr!! 
			// only when it is no-available, it can be excluded. But we test it in configuration part.
			withLifecycle = true;
			//lifecycleIdx = eventColumns.indexOf(config.getMLifecycle().getStringValue());
			lifecycleIdx = eventList.indexOf(m_settings.life_cycle);
			eventColVisited[lifecycleIdx] =  true;
		}
		
		if(Table2XLogConverterNodeSettings.isNoColumnSelected(m_settings.time_stamp)) {
			throw new IllegalStateException("Time Stamp column is required.");
		}
		tsIdx = eventList.indexOf(m_settings.time_stamp);
		if(tsIdx < 0) {
			throw new IllegalStateException("Time Stamp column must be an event attribute.");
		}
		eventColVisited[tsIdx] =  true;
		
		traceColVisited[caseIDIdx] = true;
		eventColVisited[eventClassIdx] =true;
		
		
		String currentCaseID = "-1", newCaseID="";
		
		String logName = csvData.getSpec().getName();
		startLog(logName + " event log");
		
		for(DataRow row : csvData) {
			exec.checkCanceled();
			// when it is a integer or string, not matter, right?? 
			DataCell traceIDData = row.getCell(traceColIndices[caseIDIdx]);
			
			newCaseID = traceIDData.toString();
			
			if(!newCaseID.equals(currentCaseID)) {
				// we meet a new trace, end old one and begin new one
				if(!currentCaseID.equals("-1"))
					endTrace(currentCaseID); // make it as a string
				
				currentCaseID = newCaseID;
				startTrace(currentCaseID);
			}
			
			// get trace attributes 
			for(int tIdx = 0; tIdx< traceColIndices.length ; tIdx++) {
				exec.checkCanceled();
				if(traceColVisited[tIdx])
					continue; 
				if(traceAttrMap.containsKey(traceList.get(tIdx))) {
					// if contains value, compare if they are same 
					if(!traceAttrMap.get(traceList.get(tIdx)).equals(row.getCell(traceColIndices[tIdx]))) {
//						System.out.println("Error happens with the trace Attributes here");
						errorDetected = true;
						break;
					}
				}else {
					// for the values there, we deal with it later 
					traceAttrMap.put(traceList.get(tIdx), row.getCell(traceColIndices[tIdx]));
				}
			}

			// deal with new event class, it can be in discrete value
			String eventClass = null;
			DataCell eventClassData = row.getCell(eventColIndices[eventClassIdx]);
			eventClass = ((StringCell)eventClassData).getStringValue();
			
				
			try {
				// if the values there are also like this, what to do?? 
				// String cTime = ((StringCell) row.getCell(eventColIndices[cTimeIdx])).getStringValue();
				String lifecycle = null ;

				Date timeStamp = convertString2Date( row.getCell(eventColIndices[tsIdx]));
				
				// here we check the lifecycle transition and assign the values to it!! 
				if(withLifecycle) {
					// here we need to get the value from the column
					DataCell lifecycleData = row.getCell(eventColIndices[lifecycleIdx]);
					lifecycle = ((StringCell)lifecycleData).getStringValue();
					
				}
				startEvent(eventClass, timeStamp, lifecycle);
					
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			// after this, we process other attributes, like resource, costs;; At this point, we need to differ their types 
			// and add attributes to the currentEventClass...
			for(int eIdx =0; eIdx< eventColIndices.length; eIdx++) {
				exec.checkCanceled();
				if(eventColVisited[eIdx])
					continue;

				DataCell otherData = row.getCell(eventColIndices[eIdx]);
				String attrName = eventList.get(eIdx);
				assignAttributeWithDataCell(currentEvent, otherData, attrName);
			}
			
			endEvent();
		}
		// Close last trace
		endTrace(currentCaseID + "");	
		
		endLog();
	}
	
	private void assignAttributeWithDataCell(XAttributable currentObj, DataCell otherData, String attrName) {

	
		if(otherData.getType().equals(IntCell.TYPE)){
			IntCell iCell = (IntCell) otherData;
			// here we set extension as null, but later we should improve it
			assignAttribute(currentObj, factory.createAttributeDiscrete(attrName, iCell.getIntValue(), null));
		
		}else if(otherData.getType().equals(DoubleCell.TYPE)){
			DoubleCell dCell = (DoubleCell) otherData;
			// here we set extension as null, but later we should improve it
			assignAttribute(currentObj, factory.createAttributeContinuous(attrName ,dCell.getDoubleValue(), null));
		
		}else if(otherData.getType().equals(StringCell.TYPE)){
			StringCell sCell = (StringCell) otherData;
			// here we set extension as null, but later we should improve it
			assignAttribute(currentObj, factory.createAttributeLiteral(attrName ,sCell.getStringValue(), null));
		
		}else if(otherData.getType().equals(BooleanCell.TYPE)){
			BooleanCell bCell = (BooleanCell) otherData;
			// here we set extension as null, but later we should improve it
			assignAttribute(currentObj, factory.createAttributeBoolean(attrName ,bCell.getBooleanValue(), null));
		}else if(otherData.getType().equals(LocalDateTimeCellFactory.TYPE)){
			LocalDateTimeCell tCell = (LocalDateTimeCell) otherData;
			LocalDateTime ldt = tCell.getLocalDateTime();
			Date date = Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
			assignAttribute(currentObj, factory.createAttributeTimestamp(attrName, date, null));
		}else if(otherData.isMissing()) {
			// System.out.println("Missing values to attribute " + attrName); // but still we can assign it there to show missing
			assignAttribute(currentObj, factory.createAttributeLiteral(attrName ,otherData.toString(), null));
			
		}else {
			System.out.println("Unknown data type");
		}
		// here could be DateTime type, but how to say it ??
		
	}
	
	/*
	 * create a log file w.r.t. DataTable input here
	 */
	public void startLog(String logName) {
		log = factory.createLog();
		
		assignName(factory, log, logName);
		// assign EventName Classifier to log
		log.getExtensions().add(XConceptExtension.instance());
		log.getClassifiers().add(XLogInfoImpl.NAME_CLASSIFIER);
		
		// assign time stamp related attributes
		log.getExtensions().add(XTimeExtension.instance());
		log.getExtensions().add(XLifecycleExtension.instance());
		log.getClassifiers().add(XUtils.STANDARDCLASSIFIER);
		
		// add other extensions for each column here
		// for organization 
		XExtension orgExt =XOrganizationalExtension.instance();	
		log.getExtensions().add(orgExt);
		
	}
	
	// we end the log by assigning the global attributes			
	public void endLog() {
		if(!log.isEmpty()) {
			// after this operation, traverse the trace and get its attributes from it
			// better way to do this is to assign it after this operation!!!
			List<XAttribute> tAttrs = XLogUtil.getTAttributes(log, 0.2);
			List<XAttribute> eAttrs = XLogUtil.getEAttributes(log, 0.2);
			log.getGlobalTraceAttributes().addAll(tAttrs);
			log.getGlobalEventAttributes().addAll(eAttrs);
		}
	}
	
	public void startTrace(String caseId) {
		currentEvents.clear();
		// clear map and begin a new one
		traceAttrMap.clear();
		errorDetected = false;
		currentTrace = factory.createTrace();
		
		assignName(factory, currentTrace, caseId);
	}

	
	
	public void endTrace(String caseId) {
		if (errorDetected && m_settings.error_handling.equals(CSVErrorHandlingMode.OMIT_TRACE_ON_ERROR.toString())) {
			// Skip the entire trace
			logger.warn("Unmatch trace attribute values error is detected on the trace, therefore trace is omitted");
			return;
		}
		
		currentTrace.addAll(currentEvents);
		// add trace attribute to currentTrace 
		for(String attrKey : traceAttrMap.keySet()) {
			DataCell data = traceAttrMap.get(attrKey);
			assignAttributeWithDataCell(currentTrace, data, attrKey);
			
		}
		
		log.add(currentTrace);
	}
	
	public void startEvent(String eventClass, Date timeStamp, String lifecycle) {
		if(m_settings.error_handling.equals(CSVErrorHandlingMode.OMIT_EVENT_ON_ERROR.toString()))
			// Include the other events in that trace
			errorDetected = false;
		
		
		currentEvent = factory.createEvent();
		
		assignName(factory, currentEvent, eventClass);
		assignTimestamp(factory, currentEvent, timeStamp);
		
//		if(instance!=null)
//			assignInstance(factory, currentEvent, instance);
		// just add the time stamp with the corresponding lifecycle
		if(lifecycle != null) {
			// find the corresponding conversion for the lifecycle to standard model change!
			assignLifecycleTransition(factory, currentEvent, lifecycle);
		}
	}
	
	public void endEvent() {
		if (errorDetected && m_settings.error_handling.equals(CSVErrorHandlingMode.OMIT_EVENT_ON_ERROR.toString())) {
			// Do not include the event
			return;
		}
		// Add start event before complete event to guarantee order for events with same time-stamp
		if (currentStartEvent != null) {
			currentEvents.add(currentStartEvent);
			currentStartEvent = null;
		}
		currentEvents.add(currentEvent);
		currentEvent = null;
		
	}
	
	
	public XLog getXLog() {
		return log;
	}
	
	private static void assignAttribute(XAttributable a, XAttribute value) {
		XUtils.putAttribute(a, value);
	}

	private static void assignLifecycleTransition(XFactory factory, XAttributable a, String lifecycle) {
		// here we don't use the standard model implictly. How to get the transition value??
		// StandardModel lcModel = StandardModel.valueOf(lifecycle);
		// this is one way, another way, we just assign the lifecycle transition directly as one attribute
//		XesLifecycleTransition lfTransition = XesLifecycleTransition.valueOf(lifecycle);
//		
//		assignAttribute(a, factory.createAttributeLiteral(XLifecycleExtension.KEY_TRANSITION, lfTransition.getTransition(),
//				XLifecycleExtension.instance()));
		
		assignAttribute(a, factory.createAttributeLiteral(XLifecycleExtension.KEY_TRANSITION, lifecycle,
				XLifecycleExtension.instance()));
	}
	

	private static void assignName(XFactory factory, XAttributable a, String value) {
		// here if we assign it as the concept:name or as the activity?? If at end, all the 
		assignAttribute(a,
				factory.createAttributeLiteral(XConceptExtension.KEY_NAME, value, XConceptExtension.instance()));
	}


	private static void assignTimestamp(XFactory factory, XAttributable a, Date value) {
		assignAttribute(a,
				factory.createAttributeTimestamp(XTimeExtension.KEY_TIMESTAMP, value, XTimeExtension.instance()));
	}
	
	// convert string to DateTime there, one easy solution is to delete the zone in data and time
	// here we assume the dataCell is in DataTime format in KNIME
	public Date convertString2Date(DataCell dataCell) throws ParseException {
		if(dataCell.getType().equals(LocalDateTimeCellFactory.TYPE)) {
			// it is one local time cell
			LocalDateTimeCell dataValue = (LocalDateTimeCell) dataCell;
			return Date
		      .from(dataValue.getLocalDateTime().atZone(ZoneId.systemDefault())
		      .toInstant());
		}else if(dataCell.getType().equals(ZonedDateTimeCellFactory.TYPE)) {
			ZonedDateTimeCell dataValue = (ZonedDateTimeCell) dataCell;
			return Date.from(dataValue.getZonedDateTime().toInstant());
		}
		return null;
	}


	public void setLogger(NodeLogger logger) {
		// TODO Auto-generated method stub
		this.logger = logger;
	}

	
}
