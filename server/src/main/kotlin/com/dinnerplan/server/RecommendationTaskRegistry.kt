package com.dinnerplan.server

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import java.util.concurrent.ConcurrentHashMap

class RecommendationTaskRegistry {
    private val cookJobs = ConcurrentHashMap<String, Job>()

    fun register(requestId: String, job: Job) {
        cookJobs[requestId] = job
    }

    fun complete(requestId: String) {
        cookJobs.remove(requestId)
    }

    fun cancel(requestId: String): Boolean {
        val job = cookJobs.remove(requestId) ?: return false
        job.cancel(CancellationException("AI 菜谱制作已取消"))
        return true
    }

    fun isActive(requestId: String): Boolean {
        return cookJobs[requestId]?.isActive == true
    }
}
