package hw4Query2;

import java.io.IOException;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.hadoop.util.LineReader;


public class HW4Query2 {
	
	public static class HW4Query2InputFormat extends FileInputFormat<LongWritable,Text>{
//		public HW4Query2InputFormat() {
//			// TODO Auto-generated constructor stub
//		}
		
	    @Override
	    public RecordReader<LongWritable, Text> createRecordReader(
	            InputSplit split, TaskAttemptContext context) throws IOException,
	            InterruptedException {
	        return new HW4CustomLineRecordReader();
	    }
	}
	
	
	public static class HW4CustomLineRecordReader extends RecordReader<LongWritable, Text> {
		 
	    private long start;
	    private long pos;
	    private long end;
	    private LineReader in;
	    private int maxLineLength;
	    private Text newValue = new Text();
	 	 
	    /**
	     * From Design Pattern, O'Reilly...
	     * This method takes as arguments the map task’s assigned InputSplit and
	     * TaskAttemptContext, and prepares the record reader. For file-based input
	     * formats, this is a good place to seek to the byte position in the file to
	     * begin reading.
	     */
	    @Override
	    public void initialize(
	            InputSplit genericSplit, 
	            TaskAttemptContext context)
	            throws IOException {
	 
	        // This InputSplit is a FileInputSplit
	        FileSplit split = (FileSplit) genericSplit;
	 
	        // Retrieve configuration, and Max allowed
	        // bytes for a single record
	        Configuration job = context.getConfiguration();
	        this.maxLineLength = job.getInt(
	                "mapred.linerecordreader.maxlength",
	                Integer.MAX_VALUE);
	 
	        // Split "S" is responsible for all records
	        // starting from "start" and "end" positions
	        start = split.getStart();
	        end = start + split.getLength();
	 
	        // Retrieve file containing Split "S"
	        final Path file = split.getPath();
	        FileSystem fs = file.getFileSystem(job);
	        FSDataInputStream fileIn = fs.open(split.getPath());
	 
	        // If Split "S" starts at byte 0, first line will be processed
	        // If Split "S" does not start at byte 0, first line has been already
	        // processed by "S-1" and therefore needs to be silently ignored
	        boolean skipFirstLine = false;
	        if (start != 0) {
	            skipFirstLine = true;
	            // Set the file pointer at "start - 1" position.
	            // This is to make sure we won't miss any line
	            // It could happen if "start" is located on a EOL
	            --start;
	            fileIn.seek(start);
	        }
	 
	        in = new LineReader(fileIn, job);
	 
	        // If first line needs to be skipped, read first line
	        // and stores its content to a dummy Text
	        if (skipFirstLine) {
	            Text dummy = new Text();
	            // Reset "start" to "start + line offset"
	            start += in.readLine(dummy, 0,
	                    (int) Math.min(
	                            (long) Integer.MAX_VALUE, 
	                            end - start));
	        }
	 
	        // Position is the actual start
	        this.pos = start;
	 
	    }
	 
	    /**
	     * From Design Pattern, O'Reilly...
	     * Like the corresponding method of the InputFormat class, this reads a
	     * single key/ value pair and returns true until the data is consumed.
	     */
	    @Override
	    public boolean nextKeyValue() throws IOException {
	 
	        // Current offset is the key
	    	LongWritable key = new LongWritable();
	    	Text value = new Text();
	    	
	    	String jsonString = "" ;
	    	int newSize=0;
			value.clear();
			
	        // Make sure we get at least one record that starts in this Split
	        while (pos < end) {
	        	key.set(pos);
	       	 
	 
	            // Read first line and store its content to "value"
	            newSize = in.readLine(value, maxLineLength,
	                    Math.max((int) Math.min(
	                            Integer.MAX_VALUE, end - pos),
	                            maxLineLength));
	 
	            // No byte read, seems that we reached end of Split
	            // Break and return false (no key / value)
	            if (newSize == 0) {
	                return false;
	            }
	 
	            // Line is read, new position is set
	            pos += newSize;
	            
	            //json begin with "{", maybe several "{"
	            jsonString = value.toString();
	            
	            while(jsonString.contains("\"")==false){
	            	newSize = in.readLine(value, maxLineLength,
	                        Math.max((int) Math.min(
	                                Integer.MAX_VALUE, end - pos),
	                                maxLineLength));
	            	if (newSize == 0) {
	                    return false;
	                }
	                pos += newSize;
	                jsonString = value.toString();
	            }
	            
	            //jsonString.replace("{", "");
	            newValue.set(jsonString.split(":")[1].replace("\n",","));
	            newSize = in.readLine(value, maxLineLength,
	                    Math.max((int) Math.min(
	                            Integer.MAX_VALUE, end - pos),
	                            maxLineLength));
				pos += newSize;
	            jsonString = value.toString();
	            while(jsonString.contains("}")==false)
				   {
	            	   newValue.set(newValue.toString()+jsonString.split(":")[1].replace("\n",","));
	            	   newSize = in.readLine(value, maxLineLength,
	                           Math.max((int) Math.min(
	                                   Integer.MAX_VALUE, end - pos),
	                                   maxLineLength));
	            	   if (newSize == 0) 
					   {
							return false;
					   }
					   pos += newSize;
			           jsonString = value.toString();
				   }
	            
	            
	            // Line is lower than Maximum record line size
	            // break and return true (found key / value)
	            if (newSize < maxLineLength) {
	                return true;
	            }   
	        }
	        return false;
	    }
	 
	    /**
	     * From Design Pattern, O'Reilly...
	     * This methods are used by the framework to give generated key/value pairs
	     * to an implementation of Mapper. Be sure to reuse the objects returned by
	     * these methods if at all possible!
	     */
	    @Override
	    public LongWritable getCurrentKey() throws IOException,
	            InterruptedException {
	    	LongWritable posKey = new LongWritable();
	    	posKey.set(pos);
	    	
	        return posKey;
	    }
	 
	    /**
	     * From Design Pattern, O'Reilly...
	     * This methods are used by the framework to give generated key/value pairs
	     * to an implementation of Mapper. Be sure to reuse the objects returned by
	     * these methods if at all possible!
	     */
	    @Override
	    public Text getCurrentValue() throws IOException, InterruptedException {
	        return newValue;
	    }
	 
	    /**
	     * From Design Pattern, O'Reilly...
	     * Like the corresponding method of the InputFormat class, this is an
	     * optional method used by the framework for metrics gathering.
	     */
	    @Override
	    public float getProgress() throws IOException, InterruptedException {
	        if (start == end) {
	            return 0.0f;
	        } else {
	            return Math.min(1.0f, (pos - start) / (float) (end - start));
	        }
	    }
	 
	    /**
	     * From Design Pattern, O'Reilly...
	     * This method is used by the framework for cleanup after there are no more
	     * key/value pairs to process.
	     */
	    @Override
	    public void close() throws IOException {
	        if (in != null) {
	            in.close();
	        }
	    }

		
		
	 
		}

	
	public static class cusMapper extends
		Mapper<LongWritable, Text, Text, Text> {
		private Text outKey = new Text();
		private Text outValue = new Text();
						
		@Override
		public void map(LongWritable key, Text value, Context context) 
				throws IOException, InterruptedException{  
			// Split input string
	        String line = value.toString();
	        String[] data = line.split(",");
	        if (data.length==5){
	        	String address = data[0];
		        String customID = data[1];
		        String gender = data[2];
		        String name = data[3];
		        String salary = data[4];
		        outKey.set(salary);
		        outValue.set(gender);
		        context.write(outKey, outValue);
	        }
	        
	        
        } 
	}

	public static class cusReducer 
  		extends Reducer<Text, Text,Text,Text> {

	    public void reduce(Text key, Iterable<Text> values , 
                  Context context) throws IOException, InterruptedException {
	    	
	    	Integer male = 0;
			Integer female = 0;
			Text results = new Text();
		
	    	for (Text val:values){
	    		if (val.toString().contains("male")){
	    			male ++;
	    		}
	    		if (val.toString().contains("female")){
	    			female++;
	    		}
	    	}
	    	
	    	results.set("male:" + male.toString()+","+"female:"+female.toString());
	    	context.write(key,results);
	    }		  
	}	
	
//	public static class cusMapper extends
//		Mapper<LongWritable, Text, Text, Text> {
//		@Override
//		public void map(LongWritable key, Text value, Context context) 
//				throws IOException, InterruptedException{
//			
//			String line = value.toString();
//	        String[] data = line.split(",");
//	        if (data.length == 5){
//	        	String length = String.valueOf(data.length);
//		        String address = data[1];
//				context.write(new Text(key.toString()),new Text(address));
//
//	        }
//	        
//	        
//		}
//		
//	}

	public static void main(String[] args) throws Exception  
    {  
        
		Configuration conf = new Configuration();

		@SuppressWarnings("deprecation")
		Job job = new Job(conf);
		job.setJobName("hw4_Query2");  
		job.setJarByClass(HW4Query2.class);

        job.setMapperClass(cusMapper.class);  
        job.setReducerClass(cusReducer.class);  
        
  	  
  	    FileInputFormat.setInputPaths(job, new Path(args[0]));
		FileOutputFormat.setOutputPath(job, new Path(args[1]));
		
		job.setInputFormatClass(HW4Query2InputFormat.class);

        job.setMapOutputKeyClass(Text.class);  
        job.setMapOutputValueClass(Text.class);  
          
  	  	job.setOutputKeyClass(Text.class);
  	  	job.setOutputValueClass(Text.class); 
  
  	    System.exit(job.waitForCompletion(true) ? 0 : 1);
    
	}

	
}
