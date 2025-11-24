import { inject, Injectable } from '@angular/core';
import { ModelService } from './model.service';

@Injectable({
  providedIn: 'root',
})
export class Controller {

  modelService = inject(ModelService)

  setSignInUsername(username: string) {
    this.modelService.setSignInUsername(username)
  }

  setSignInPassword(password: string) {
    this.modelService.setSignInPassword(password)
  }

  setSignInRequestId(requestId: string) {
    this.modelService.setSignInRequestId(requestId)
  }
}
