package com.hashmap.haf.execution.controllers

import java.util.UUID

import com.github.dexecutor.core.task.ExecutionStatus
import com.google.common.base.Charsets
import com.google.common.io.Resources
import com.hashmap.haf.execution.clients.WorkflowServiceClient
import com.hashmap.haf.execution.exceptions.Exceptions.{FunctionNotFoundException, SourceCompilationException, SourceGenerationException}
import com.hashmap.haf.execution.models.Responses.{TaskError, WorkflowErrors, WorkflowExecutionResult}
import com.hashmap.haf.execution.services.WorkflowExecutionService
import com.hashmap.haf.workflow.task.SparkIgniteTask
import org.apache.ignite.Ignite
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito._
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders._
import org.springframework.test.web.servlet.result.MockMvcResultMatchers._

import scala.xml.NodeSeq

@RunWith(classOf[SpringRunner])
@WebMvcTest(value = Array(classOf[WorkflowExecutionController]), secure = false)
class WorkflowExecutionControllerTest {

	@MockBean
	private var workflowServiceClient: WorkflowServiceClient = _

	@MockBean
	private var workflowExecutionService: WorkflowExecutionService = _

	@Autowired
	private var mockMvc: MockMvc = _

	private val workflowXML: String = Resources.toString(Resources.getResource("test-success-workflow.xml"), Charsets.UTF_8)

	@Test
	def returnErrorIfWorkflowIdIsInvalid(): Unit ={
		when(workflowServiceClient.getFunction("invalid")).thenReturn(null)
		mockMvc.perform(get("/api/workflow/execute/invalid"))
			.andExpect(status().isNotFound)
			.andExpect(jsonPath("$.message").value("Workflow not found for id invalid"))
			.andExpect(jsonPath("$.details").value("uri=/api/workflow/execute/invalid"))
	}

	@Test
	def returnSuccessResponseIfAllTasksExecutedSuccessfully(): Unit ={
		val workflowId = UUID.randomUUID().toString
		when(workflowServiceClient.getFunction(workflowId)).thenReturn(workflowXML)
		when(workflowExecutionService.executeWorkflow(workflowId, workflowXML)).thenReturn(
			WorkflowExecutionResult(workflowId, ExecutionStatus.SUCCESS, None, None)
		)

		mockMvc.perform(get(s"/api/workflow/execute/$workflowId"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.id").value(workflowId))
			.andExpect(jsonPath("$.status").value(ExecutionStatus.SUCCESS.toString))
	  	.andExpect(jsonPath("$.errors").doesNotExist())
	  	.andExpect(jsonPath("$.skipped").doesNotExist())
	}

	@Test
	def returnResponseWithErrorsForTaskFailed(): Unit ={
		val workflowId = UUID.randomUUID().toString
		val taskId = UUID.randomUUID()
		when(workflowServiceClient.getFunction(workflowId)).thenReturn(workflowXML)
		when(workflowExecutionService.executeWorkflow(workflowId, workflowXML)).thenReturn(
			WorkflowExecutionResult(workflowId, ExecutionStatus.ERRORED,
				Some(WorkflowErrors(List(TaskError(taskId, "No data found in cache.")))), None)
		)

		mockMvc.perform(get(s"/api/workflow/execute/$workflowId"))
			.andExpect(status().isOk) //TODO: Need to work on status returned for errors
			.andExpect(jsonPath("$.id").value(workflowId))
			.andExpect(jsonPath("$.status").value(ExecutionStatus.ERRORED.toString))
			.andExpect(jsonPath("$.errors.taskErrors[0].id").value(taskId.toString))
			.andExpect(jsonPath("$.errors.taskErrors[0].error").value("No data found in cache."))
			.andExpect(jsonPath("$.skipped").doesNotExist())
	}

	@Test
	def returnFunctionNotFoundIfInvalidFunctionProvided(): Unit ={
		val workflowId = UUID.randomUUID().toString
		when(workflowServiceClient.getFunction(workflowId)).thenReturn(workflowXML)
		when(workflowExecutionService.executeWorkflow(workflowId, workflowXML)).thenThrow(new FunctionNotFoundException("Function dummyTask Not found"))

		mockMvc.perform(get(s"/api/workflow/execute/$workflowId"))
			.andExpect(status().isNotFound)
			.andExpect(jsonPath("$.message").value("Function dummyTask Not found"))
			.andExpect(jsonPath("$.details").value(s"uri=/api/workflow/execute/$workflowId"))
	}

	@Test
	def internalServerErrorForSourceGenerationException(): Unit ={
		val workflowId = UUID.randomUUID().toString
		when(workflowServiceClient.getFunction(workflowId)).thenReturn(workflowXML)
		when(workflowExecutionService.executeWorkflow(workflowId, workflowXML)).thenThrow(new SourceGenerationException("Error while parsing template"))

		mockMvc.perform(get(s"/api/workflow/execute/$workflowId"))
			.andExpect(status().isInternalServerError)
			.andExpect(jsonPath("$.message").value("Error while parsing template"))
			.andExpect(jsonPath("$.details").value(s"uri=/api/workflow/execute/$workflowId"))
	}

	@Test
	def internalServerErrorForSourceCompilationException(): Unit ={
		val workflowId = UUID.randomUUID().toString
		when(workflowServiceClient.getFunction(workflowId)).thenReturn(workflowXML)
		when(workflowExecutionService.executeWorkflow(workflowId, workflowXML)).thenThrow(new SourceCompilationException("Error while compiling source"))

		mockMvc.perform(get(s"/api/workflow/execute/$workflowId"))
			.andExpect(status().isInternalServerError)
			.andExpect(jsonPath("$.message").value("Error while compiling source"))
			.andExpect(jsonPath("$.details").value(s"uri=/api/workflow/execute/$workflowId"))
	}

}

class SampleWorkflowTask(xml: NodeSeq, ignite: Ignite, workflowId: String) extends SparkIgniteTask(xml){
	override def execute(): String = {
		"Success"
	}
}

class SampleFailureWorkflowTask(xml: NodeSeq, ignite: Ignite, workflowId: String) extends SparkIgniteTask(xml){
	override def execute(): String = {
		throw new RuntimeException("Simulated error in task execution")
	}
}
