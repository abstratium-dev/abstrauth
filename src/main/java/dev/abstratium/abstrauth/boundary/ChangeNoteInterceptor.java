package dev.abstratium.abstrauth.boundary;

import dev.abstratium.abstrauth.entity.ChangeNoteContext;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@Interceptor
@RequiresChangeNote
@jakarta.annotation.Priority(Interceptor.Priority.APPLICATION)
public class ChangeNoteInterceptor {

    @Context
    UriInfo uriInfo;

    @Inject
    ChangeNoteContext changeNoteContext;

    @ConfigProperty(name = "change.note.mandatory", defaultValue = "false")
    boolean changeNoteMandatory;

    @AroundInvoke
    public Object intercept(InvocationContext ctx) throws Exception {
        String changeNote = uriInfo.getQueryParameters().getFirst("changeNote");

        if (changeNoteMandatory && (changeNote == null || changeNote.isBlank())) {
            throw new jakarta.ws.rs.BadRequestException("Missing required query parameter: changeNote");
        }

        if (changeNote != null && !changeNote.isBlank()) {
            changeNoteContext.setChangeNote(changeNote);
        }

        return ctx.proceed();
    }
}
