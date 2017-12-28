package com.importsource.conc.tuts.forkjoin;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public class CountTaskTmp extends RecursiveTask<Integer> {

	private static final int THRESHOLD = 2;
	
	private int start;
	
	private int end;
	
	public CountTaskTmp(int start, int end) {
		this.start = start;
		this.end = end;
	}


	@Override
	protected Integer compute() {
		int sum = 0;
		boolean canCompute = (end - start) <= THRESHOLD;
		if (canCompute) {
			for (int i = start; i <= end; i++)
				sum += i;
		} else {
			//如果任务大于阀值，就分裂成两个子任务计算
			int mid = (start + end) / 2;
			CountTask leftTask = new CountTask(start, mid);
			CountTask rightTask = new CountTask(mid+1, end);
			
			//执行子任务
			leftTask.fork();
			rightTask.fork();
			
			//等待子任务执行完，并得到结果
			int leftResult = (int)leftTask.join();
			int rightResult = (int)rightTask.join();
			
			sum = leftResult + rightResult;
		}
		
		return sum;
	}
	

	public static void main(String[] args) {
		ForkJoinPool forkJoinPool = new ForkJoinPool();
		
        //生成一个计算资格，负责计算1+2+3+4  
        CountTask task = new CountTask(1, 4); 
        //Future<Integer> result = forkJoinPool.submit(task);
        try {
        	//System.out.println(result.get());
		} catch (Exception e) {
		}
	}
}
