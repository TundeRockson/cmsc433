package cmsc433.p5;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

/**
 * Map reduce which takes in a CSV file with tweets as input and output
 * key/value pairs.</br>
 * </br>
 * The key for the map reduce depends on the specified {@link TrendingParameter}
 * , <code>trendingOn</code> passed to
 * {@link #score(Job, String, String, TrendingParameter)}).
 */
public class TweetPopularityMR {

	// For your convenience...
	public static final int          TWEET_SCORE   = 1;
	public static final int          RETWEET_SCORE = 3;
	public static final int          MENTION_SCORE = 1;
	public static final int			 PAIR_SCORE = 1;

	// Is either USER, TWEET, HASHTAG, or HASHTAG_PAIR. Set for you before call to map()
	private static TrendingParameter trendingOn;

	public static class TweetMapper
	extends Mapper<LongWritable,Text,Text,IntWritable> {
		private final static IntWritable uno = new IntWritable(TWEET_SCORE);
		private final static IntWritable tres = new IntWritable(RETWEET_SCORE);
		private Text word = new Text();

		@Override
		public void map(LongWritable key, Text value, Context context)
				throws IOException, InterruptedException {
			// Converts the CSV line into a tweet object
			Tweet tweet = Tweet.createTweet(value.toString());

			// TODO: Your code goes here
			if(trendingOn == TrendingParameter.USER) {
				word.set(tweet.getUserScreenName());
				context.write(word, uno);
				if(tweet.wasRetweetOfUser()) {
					word.set(tweet.getRetweetedUser());
					context.write(word, tres);
				}
				List<String> mentionedUsers = tweet.getMentionedUsers();
				for(String m: mentionedUsers) {
					word.set(m);
					context.write(word, uno);
				}
			}else if(trendingOn == TrendingParameter.TWEET) {
				String id = tweet.getId().toString();
				word.set(id);
				context.write(word,uno );
				if(tweet.wasRetweetOfTweet()) {
					String oID = tweet.getRetweetedTweet().toString();
					word.set(oID);
					context.write(word, tres);
				}
			}else if(trendingOn == TrendingParameter.HASHTAG) {
				List<String>hashTags = tweet.getHashtags();
				for(String h : hashTags) {
					word.set(h);
					context.write(word, uno);
				}
			}else if(trendingOn == TrendingParameter.HASHTAG_PAIR) {
				List<String> hashTags_list = tweet.getHashtags();
				Collections.sort(hashTags_list);
				if(hashTags_list.size() > 1) {
					for (int i = 0; i < hashTags_list.size()-1; i++) {
						for(int j = i+1; j < hashTags_list.size(); j++) {
							word.set("("+hashTags_list.get(i)+","+hashTags_list.get(j)+")");
							context.write(word, uno);
						}
					}
				}
			}





		}
	}

	public static class PopularityReducer
	extends Reducer<Text,IntWritable,Text,IntWritable> {

		@Override
		public void reduce(Text key, Iterable<IntWritable> values, Context context)
				throws IOException, InterruptedException {

			int sum = 0;
			for (IntWritable val : values) {
				sum += val.get();
			}
			context.write(key, new IntWritable(sum));

		}
	}

	/**
	 * Method which performs a map reduce on a specified input CSV file and
	 * outputs the scored tweets, users, or hashtags.</br>
	 * </br>
	 * 
	 * @param job
	 * @param input
	 *          The CSV file containing tweets
	 * @param output
	 *          The output file with the scores
	 * @param trendingOn
	 *          The parameter on which to score
	 * @return true if the map reduce was successful, false otherwise.
	 * @throws Exception
	 */
	public static boolean score(Job job, String input, String output,
			TrendingParameter trendingOn) throws Exception {

		TweetPopularityMR.trendingOn = trendingOn;

		job.setJarByClass(TweetPopularityMR.class);

		// TODO: Set up map-reduce...
		job.setMapperClass(TweetMapper.class);
		job.setReducerClass(PopularityReducer.class);

		job.setInputFormatClass(TextInputFormat.class);
		job.setOutputFormatClass(TextOutputFormat.class);

		job.setMapOutputKeyClass(Text.class);
		job.setMapOutputValueClass(IntWritable.class);

		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(IntWritable.class);



		// End

		FileInputFormat.addInputPath(job, new Path(input));
		FileOutputFormat.setOutputPath(job, new Path(output));

		return job.waitForCompletion(true);
	}
}
