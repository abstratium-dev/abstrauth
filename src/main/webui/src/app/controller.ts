import { inject, Injectable } from '@angular/core';
import { ModelService } from './model.service';

@Injectable({
  providedIn: 'root',
})
export class Controller {

  modelService = inject(ModelService)

  setSignUpUsername(username: string) {
    this.modelService.setSignUpUsername(username)
  }

  setSignUpPassword(password: string) {
    this.modelService.setSignUpPassword(password)
  }

  setSignInRequestId(requestId: string) {
    this.modelService.setSignInRequestId(requestId)
  }
}
